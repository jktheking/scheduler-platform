## Step 0 — Define workflow (scheduler-api → DB)

### What happens

* `WorkflowDefinitionController` (`POST /api/v1/workflow-definitions`) receives:

  * `taskDefinitionJson` (tasks array)
  * `taskRelationJson` (edges array)
  * optional `workflowCode` (if you want a new version for an existing workflow)
  * optional `slaSeconds`
* `UpsertWorkflowDefinitionUseCase` parses tasks + edges and calls `WorkflowDefinitionGateway.upsertWorkflowDefinition(...)`.
* JDBC implementation `JdbcWorkflowDefinitionGateway`:

  1. Allocates `workflow_code` (if missing) from `seq_workflow_code`
  2. Allocates missing `task_code` from `seq_task_code`
  3. Upserts **task definitions** into `t_task_definition`
  4. Builds and stores **workflow `definition_json`** (includes `slaSeconds`, `tasks[]`, `edges[]`) into `t_workflow_definition`
  5. Replaces edges for that version in `t_workflow_dag_edge`

### DB state

* `t_task_definition` (upsert per `(task_code, task_version)`):

  * `tenant_id`, `name`, `task_type` (`HTTP` or `SCRIPT`), `definition_json`
* `t_workflow_definition` (insert new version):

  * `(tenant_id, workflow_code, workflow_version)` created
  * `definition_json` contains:

    * optional `"slaSeconds": <long>`
    * `"tasks": [...]` and `"edges": [...]`
* `t_workflow_dag_edge`:

  * rows inserted for `(workflow_code, workflow_version, from_task_code, to_task_code)`

### Metrics

* No dedicated “definition created” counter in code.
* You will still see normal Spring HTTP metrics + OTel traces.

### SLA / boundaries

* No enforcement here. This step only persists DAG + optional SLA config.

---

## Step 1 — Start workflow instance (scheduler-api → scheduler-service → CommandBus → Kafka WAL)

### What happens

* `WorkflowExecutionController` (`POST /api/v1/projects/{projectCode}/workflow-instances`) receives start request.
* Builds payload JSON from request; if request has `traceparent`, it adds it into payload as `"traceParent"`.
* Calls `StartWorkflowUseCase.execute(...)`:

  1. `AdmissionController.decide(tenantId, "workflow.start")`
  2. Creates a `CommandEnvelope` with:

     * `commandType = "START_PROCESS"`
     * `tenantId`, `workflowCode`, `workflowVersion=1` (demo uses 1)
     * `createdAt = Instant.now()`
     * `payloadJson` (includes scheduleTime/idempotencyKey/traceParent if present)
  3. Calls `CommandIngestionGateway.ingest(cmd)`

     * In demo Kafka mode: `KafkaWalCommandIngestionGateway` → `CommandBus.publish(cmd)`
  4. Returns **immediately** with `SUBMITTED` placeholder (no workflow_instance_id yet)

### DB state

* None (in Kafka mode). This is a WAL append.

### Metrics (emitted here)

From `MeteredCommandBus`:

* `scheduler_command_publish_total{command_type,tenant_id}`
* `scheduler_command_publish_failed_total{command_type,tenant_id}`

### SLA / boundaries

* **Durability boundary (Kafka WAL):** “accepted” means the command was published to Kafka.
* **Backpressure boundary:** admission control may reject (HTTP 429) before publishing.

---

## Step 2 — Kafka WAL consumers (`scheduler.commands.v1`)

Your code has **two distinct things** tied to commands:

### 2.1 KafkaCommandWriterLoop (Kafka WAL → `t_command` raw persistence)

**Class:** `KafkaCommandWriterLoop` (consumer group: `scheduler-command-writer`)

#### What happens

* Subscribes to `scheduler.commands.v1`
* For each record:

  * deserializes `CommandEnvelope`
  * `JdbcCommandWriter.insertIfAbsent(cmd)` → persists raw command into `t_command` using `ON CONFLICT DO NOTHING`
* Commits offsets when batch is processed

#### DB state (`t_command`)

* Insert once per `command_id` (idempotent)
* Typical fields (per schema intent):

  * `tenant_id`, `command_id`, `command_type`, `workflow_code`, `workflow_version`, `payload_json`
  * `status` (starts as e.g. `NEW`)
  * `last_error_message` nullable

#### Metrics

* No “success” counter in this loop.
* On exceptions: `scheduler.master.command.kafka.error` (via `MasterMetrics.commandKafkaError`)

#### SLA / boundaries

* This makes Kafka WAL **recoverable/auditable** in Postgres.
* **Important truth from this zip:** `t_command` is *not* the execution queue in Kafka mode; it’s the persisted WAL copy.

---

### 2.2 KafkaMasterCommandConsumerLoop (orchestrator materialization → instance/plan/trigger)

**Class:** `KafkaMasterCommandConsumerLoop`

#### What happens

* Consumes `scheduler.commands.v1` with correctness fences:

  * **Non-sharded mode:** only leader of shard `0` subscribes/polls
  * **Sharded mode:** assigns only partitions for shards it leads; refuses to apply commands for shards it does not lead; won’t commit offsets if any record wasn’t applied
* Dedupes command application using `JdbcCommandDedupeRepository`:

  * `tryMarkProcessing(commandId)` → only first one proceeds
  * `markOutcome(commandId, DONE|FAILED, detail)`
* For `START_PROCESS`:

  * calls `WorkflowScheduler.createAndSchedule(...)`
  * JDBC impl is `JdbcWorkflowScheduler`

#### DB state transitions

* `t_command_dedupe`

  * insert once per `command_id`
  * outcome transitions: `PROCESSING` → `DONE` or `FAILED` (+ detail)
* `t_workflow_instance` (created by master)

  * insert:

    * `status='TRIGGERED'`
    * `schedule_time` = parsed from payload `"scheduleTime"` (ISO instant or epoch millis), clamped to `now` if in past
* `t_workflow_plan`

  * insert `(workflow_instance_id, plan_json)` = **command payload JSON**
  * (idempotent with `ON CONFLICT DO NOTHING`)
* `t_trigger`

  * insert initial trigger:

    * `status='DUE'`
    * `due_time = schedule_time`
    * `shard_id` set (0 in non-sharded; computed in sharded mode)
    * `trigger_type` defaults to `'DAG_WAKE'` per schema

#### Metrics

From `MasterMetrics`:

* `scheduler.master.command.kafka.consumed`
* `scheduler.master.command.kafka.error`
* `scheduler.master.workflow.scheduled`

#### SLA / boundaries

* **Leader fence:** only leader applies “create instance” side effects.
* **Dedup fence:** `t_command_dedupe` makes at-least-once Kafka safe for DB materialization.

---

## Step 3 — Trigger engine (scheduler-master): “due triggers → DAG wake / SLA wake”

**Engine:** `TimeWheelTriggerEngine`

### What happens

* Loader loop:

  * loads upcoming triggers up to `now + lookaheadMs`:

    * `JdbcTriggerRepository.loadUpcoming(...)`
  * flips `DUE → ENQUEUED` via `markEnqueued(trigger_id)` to reduce repeat loading
  * enqueues into in-memory wheel shard/slot
* Tick loop:

  * drains slots
  * claims due triggers from DB using `claimDue(...)`:

    * SQL uses `FOR UPDATE SKIP LOCKED`
    * updates `status → PROCESSING`, sets `claimed_by`, `claimed_at`
  * For each claimed trigger:

    * if `trigger_type == WORKFLOW_SLA` → `dagRuntime.onWorkflowSlaDue(...)`
    * else → `dagRuntime.onTriggerDue(...)`
  * Marks trigger terminal:

    * `PROCESSING → DONE` or `FAILED` (+ `last_error`)

### DB trigger state transitions (`t_trigger.status`)

* Inserted by master scheduler: `DUE`
* Loader may flip: `DUE → ENQUEUED`
* Claimer flips: `DUE|ENQUEUED → PROCESSING`
* Terminal:

  * `PROCESSING → DONE`
  * or `PROCESSING → FAILED` (+ `last_error`)

### Metrics

From `MasterMetrics`:

* `scheduler.master.trigger.claimed` (adds batch size)
* `scheduler.master.trigger.processed`
* `scheduler.master.trigger.error`

### SLA / boundaries

* **Concurrency boundary:** `SKIP LOCKED` makes multi-master safe for claiming triggers.
* **Latency knobs (from constructor/config):** `tickMs`, `lookaheadMs`, `slots`, `drainBatch`, `shards`.

---

## Step 4 — DAG runtime wakeup (master): materialize tasks + publish READY

**Runtime:** `DefaultDagRuntime.onTriggerDue(...)`

### What happens

1. **Leader fence**

   * Non-sharded: must be leader(0)
   * Sharded: must be leader of `shardForWorkflowInstanceId(workflowInstanceId)`
2. Ignores terminal workflows (`SUCCESS|FAILURE`)
3. `wi.markWorkflowRunningIfStart(...)`

   * `TRIGGERED|CREATED → RUNNING`
   * returns `startedNow=true` only on the first transition
4. `WorkflowMaterializer.materializeIfAbsent(...)`

   * Loads workflow `definition_json` from `t_workflow_definition`
   * Loads required task definitions from `t_task_definition`
   * Inserts attempt 0 rows into `t_task_instance` (idempotent):

     * status `PENDING`, attempt `0`, payload_json = task definition JSON
   * Builds DAG from edges (prefers `definition_json.edges`, fallback to `t_workflow_dag_edge`)
   * Computes `roots`
5. **Master-owned workflow SLA scheduling (real enforcement in this zip)**

   * Only when `startedNow == true`
   * Extracts `slaSeconds` from workflow `definition_json` root
   * If `slaSeconds > 0`:

     * schedules **one** SLA trigger:

       * `JdbcTriggerRepository.scheduleWorkflowSlaTrigger(...)`
       * inserts into `t_trigger` with `trigger_type='WORKFLOW_SLA'`, `status='DUE'`
       * unique constraint ensures “at most one per workflow instance”
6. Retry handling:

   * `tasks.moveRetryWaitToQueued(workflowInstanceId, now)` when `next_run_time <= now`
7. Ensures roots are queued:

   * `PENDING|RETRY_WAIT → QUEUED` for attempt `0` root task codes
8. Dispatch loop:

   * selects `t_task_instance.status='QUEUED'` (bounded 500)
   * builds `TaskDispatchEnvelope` and publishes via `KafkaReadyPublisher`

     * topic = master config `readyTopic` (demo: `scheduler.tasks.ready.v1`)

### DB states

`t_workflow_instance.status`

* `TRIGGERED → RUNNING` (first DAG wake)
* later:

  * `RUNNING → SUCCESS` (when all latest attempts end in `SUCCESS|SKIPPED`)
  * or `RUNNING → FAILURE` (SLA breach or DLQ/Workflow failed path)

`t_task_instance.status` (attempt 0 materialization)

* `PENDING` on insert
* `PENDING|RETRY_WAIT → QUEUED` when eligible
* (worker then drives: `QUEUED → CLAIMED → RUNNING → SUCCESS|FAILED|DLQ`)

### Metrics

From `MasterMetrics`:

* `scheduler.master.dag.materialize.count`
* `scheduler.master.dag.enqueue.count`
* from `KafkaReadyPublisher` callbacks:

  * `scheduler.master.ready.published`
  * `scheduler.master.ready.publish.error`

### SLA / boundaries implemented (in code)

**Workflow SLA (master-owned, enforced)**

* Config source: workflow `definition_json.slaSeconds`
* Scheduling: once at workflow start (`RUNNING` transition)
* Enforcement: `onWorkflowSlaDue(...)`

  * if workflow still not terminal:

    * marks `t_workflow_instance.status='FAILURE'` with `last_error="Workflow SLA breached"`
    * publishes alert `AlertType.SLA_BREACH` via `KafkaAlertPublisher`

---

## Step 5 — Worker consumes READY (scheduler-worker): claim + execute + publish TASK STATE

### What happens

* `KafkaReadyTaskConsumerLoop` consumes `scheduler.tasks.ready.v1`
* Parses wrapper, extracts `TaskDispatchEnvelope`
* `WorkerTaskOrchestrator.onDispatch(env)`:

  1. Loads row from DB (`JdbcTaskInstanceRepository.load`)
  2. Attempts durable claim:

     * `tryClaim(taskInstanceId, attempt, workerId)`
     * only succeeds if status is `QUEUED`
  3. On claim success:

     * marks `RUNNING`
     * executes based on `taskType`:

       * `HTTP`: `HttpTaskExecutor`
       * `SCRIPT`: `ScriptTaskExecutor`
  4. Marks terminal DB status:

     * `SUCCESS` or `FAILED` (+ `last_error`) (and timestamps)
  5. Publishes `TaskStateEvent` to Kafka topic `scheduler.task.state.v1` using `KafkaTaskStatePublisher`

### DB task state transitions (`t_task_instance.status`)

* `QUEUED → CLAIMED` (durable idempotent claim)
* `CLAIMED → RUNNING`
* terminal:

  * `RUNNING|CLAIMED → SUCCESS`
  * `RUNNING|CLAIMED → FAILED` (+ `last_error`)
  * (DLQ is set by master/DB, worker can mark DLQ only if you call `markDlq` explicitly)

### Metrics (worker)

From `WorkerMetrics` + loops:

* Ready consume:

  * `scheduler.worker.ready.consumed.count`
  * `scheduler.worker.ready.consume.error`
* Claim/execute:

  * `scheduler.worker.task.claimed.count{taskType}`
  * `scheduler.worker.task.started.count{taskType}`
  * `scheduler.worker.task.success.count{taskType}`
  * `scheduler.worker.task.fail.count{taskType}`
  * `scheduler.worker.task.runtime.ms{taskType}` (timer)
  * `scheduler.worker.task.queue_delay.ms{taskType}` (timer)
* State publish:

  * `scheduler.worker.state.published.count`
  * `scheduler.worker.state.publish.error`

### SLA / boundaries

* **Exactly-once effect boundary for execution:** claim in DB (`status='QUEUED' → 'CLAIMED'`) makes duplicate Kafka deliveries safe.
* Worker enforces execution timeouts at runtime (master does not kill tasks; master only alerts).

---

## Step 6 — Master consumes TASK STATE (scheduler.task.state.v1): progress DAG + retry + DLQ + fail workflow

### What happens

* `KafkaTaskStateConsumerLoop` consumes `scheduler.task.state.v1` with the same leader/shard fences as command consumer.
* For each `TaskStateEvent`, master calls `DefaultDagRuntime.onTaskState(evt)`:

  1. Ignores if not leader for that workflow instance
  2. Ignores if workflow already terminal
  3. Loads current task meta from DB and **ignores stale attempts**:

     * if `evt.attempt != currentAttemptInDB` → skip (prevents retry races)
  4. Switch by `evt.state()`:

     * `SUCCEEDED` or `SKIPPED`:

       * `DagProgressionEngine.onTaskTerminalSuccess(...)`
       * unblocks children when all parents are `SUCCESS|SKIPPED`
       * marks child attempt0 `QUEUED`
       * publishes READY for unblocked tasks
       * if all latest attempts are terminal success → marks workflow `SUCCESS`
     * `FAILED`:

       * `RetryPlanner.scheduleRetry(...)`

         * if retry budget exceeded:

           * `JdbcDagTaskInstanceRepository.moveToDlq(...)`
           * publishes alert `AlertType.DLQ_CREATED`
         * else:

           * inserts attempt+1 row `RETRY_WAIT` with `next_run_time`
           * schedules new `t_trigger` (`trigger_type='DAG_WAKE'`, status `DUE`) at `next_run_time`
     * `DLQ`:

       * marks workflow `FAILURE` if not already terminal
       * publishes alert `AlertType.WORKFLOW_FAILED`

### DB state changes (master reactions)

Retry path:

* `t_task_instance`:

  * attempt+1 inserted as `RETRY_WAIT` with `next_run_time`
* `t_trigger`:

  * new `DAG_WAKE` trigger scheduled due at `next_run_time`

DLQ path:

* `t_dlq_task`:

  * row inserted with `reason` and `payload_json`
* `t_task_instance.status`:

  * set `DLQ`
* `t_workflow_instance.status`:

  * may become `FAILURE` depending on event path

### Metrics (master)

From `MasterMetrics`:

* Task state consume:

  * `scheduler.master.task.state.kafka.consumed`
* DAG progression:

  * `scheduler.master.dag.progress.unblocked.count`
  * `scheduler.master.dag.enqueue.count`
* Retry/DLQ:

  * `scheduler.master.retry.scheduled.count`
  * `scheduler.master.dlq.created.count`
  * `scheduler.master.dlq.replay.count` (for `REPLAY_DLQ` command type)
* Alerts:

  * `scheduler.master.alert.published`
  * `scheduler.master.alert.publish.error`

---

## Step 7 — Alert delivery (scheduler-alert-server): consume alerts + dispatch

*(This exists in your zip; master actually publishes alerts to Kafka.)*

### What happens

* Master publishes `AlertEvent` to topic (default): `scheduler.alerts.v1` via `KafkaAlertPublisher`
* `scheduler-alert-server` consumes `scheduler.alerts.v1` (group `scheduler-alert-server`)
* Default dispatcher (`LoggingAlertDispatcher`) logs; optional webhook URLs can be configured to POST events.

### DB state

* None (alerts are Kafka-driven in this demo).

### Metrics

* On master side only (already listed):

  * `scheduler.master.alert.published`
  * `scheduler.master.alert.publish.error`

---


## Is `t_command` used in processing, or only storage?


* In **Kafka ingestion mode**, `t_command` is written by `KafkaCommandWriterLoop` as a **persisted copy of Kafka WAL** (audit/recovery).
* `t_command` **can also become an actual processing queue** only when you run **DB poll mode** on master:

  * `MasterSchedulerLoop` polls `CommandQueue.claimBatch(...)` and increments:

    * `scheduler.master.command.claimed`
    * (and then processes commands)
      So: **yes, `t_command` is used for processing in the DB-poll path**, but **in the Kafka path it’s primarily WAL persistence + replay/audit support**, while orchestration materialization is done by `KafkaMasterCommandConsumerLoop`.
