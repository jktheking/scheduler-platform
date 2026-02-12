# Sequence Diagram — Master Consume → Materialize → Trigger → READY

This is the **master-side path** after a command is already in Kafka WAL (`scheduler.commands.v1`).

- **KafkaMasterCommandConsumerLoop** applies commands (leader/shard gated) and materializes:
  - `t_command_dedupe`
  - `t_workflow_instance`
  - `t_workflow_plan`
  - initial `t_trigger` (DAG wake)
- **TimeWheelTriggerEngine** claims due triggers (`FOR UPDATE SKIP LOCKED`) and calls DAG runtime.
- DAG runtime queues eligible tasks in DB and **publishes dispatch envelopes** to `scheduler.tasks.ready.v1`.

```mermaid
sequenceDiagram
  autonumber

  participant K as Kafka (scheduler.commands.v1)
  participant MC as KafkaMasterCommandConsumerLoop
  participant DD as JdbcCommandDedupeRepository
  participant WS as JdbcWorkflowScheduler
  participant WI as JdbcWorkflowInstanceRepository
  participant PL as JdbcWorkflowPlanRepository
  participant TR as JdbcTriggerRepository

  participant TE as TimeWheelTriggerEngine
  participant DR as DefaultDagRuntime
  participant TI as JdbcTaskInstanceRepository
  participant RP as KafkaReadyPublisher
  participant KR as Kafka (scheduler.tasks.ready.v1)

  MC->>K: poll()
  K-->>MC: CommandEnvelope

  MC->>DD: tryMarkProcessing(commandId)
  alt duplicate delivery
    DD-->>MC: false
    MC-->>K: commit offset (safe no-op)
  else first time
    DD-->>MC: true
    MC->>WS: createAndSchedule(command)
    WS->>WI: insert workflow_instance (status=TRIGGERED)
    WI-->>WS: workflow_instance_id
    WS->>PL: upsert plan_json
    PL-->>WS: ok
    WS->>TR: insert trigger (type=DAG_WAKE, status=DUE, due_time=schedule_time)
    TR-->>WS: ok
    WS-->>MC: ok
    MC->>DD: markOutcome(DONE)
    MC-->>K: commit offset
  end

  loop trigger tick / drain
    TE->>TR: claimDue(shard, now) (FOR UPDATE SKIP LOCKED)
    TR-->>TE: triggers[]
    TE->>DR: onTriggerDue(trigger)
    DR->>TI: queue roots + due retries (PENDING/RETRY_WAIT -> QUEUED)
    TI-->>DR: queued tasks
    DR->>RP: publish TaskDispatchEnvelope(s)
    RP->>KR: produce()
    KR-->>RP: ack
    DR-->>TE: ok
    TE->>TR: markTerminal(DONE/FAILED)
  end
```
