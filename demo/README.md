# Scheduler Platform Demo (Local Docker)

This demo runs the full end-to-end pipeline locally:

**API → Kafka (WAL commands) → Master (persist + schedule + DAG) → Kafka (ready tasks) → Worker (exec HTTP/SCRIPT) → Kafka (task state) → Master (advance DAG) → Postgres + Metrics (Prometheus/Grafana)**

---

## Quick index

- [Services & URLs](#services--urls)
- [Bring everything up](#bring-everything-up)
- [Connect to UIs (pgAdmin, Kafka UI, Prometheus, Grafana)](#connect-to-uis-pgadmin-kafka-ui-prometheus-grafana)
- [Run an end-to-end verification](#run-an-end-to-end-verification)
- [Smoke test script](#smoke-test-script)
- [Tear down & cleanup](#tear-down--cleanup)
- [Postman collection](#postman-collection)
- [Troubleshooting](#troubleshooting)

---

## Services & URLs

> All services below are defined in:
> - `demo/docker-compose.infra.yml` (infra/observability)
> - `demo/docker-compose.app.yml` (application services)

### Infra + Observability services (docker-compose.infra.yml)

| Postgres 16 | `scheduler-postgres` | `localhost:5432` | Metadata store (commands, workflow instances, triggers, task instances, idempotency) |

| pgAdmin | `scheduler-pgadmin` | http://localhost:5050 | Postgres UI |

| Kafka (KRaft) | `scheduler-kafka` | `localhost:9092` | Topics for WAL + ready tasks + state events |

| Kafka UI | `scheduler-kafka-ui` | http://localhost:8088 | Kafka browser (topics, messages, consumer groups) |

| Flyway | `scheduler-flyway` | (no UI) | Runs DB migrations on startup |

| OTel Collector | `scheduler-otel-collector` | `localhost:4317/4318` | Receives OTLP from Java services, exports metrics to Prometheus |

| Prometheus | `scheduler-prometheus` | http://localhost:9090 | Metrics store/query |

| Grafana | `scheduler-grafana` | http://localhost:3000 | Dashboards |

**Default credentials (from compose):**
- **Postgres**
  - DB: `scheduler`
  - User: `scheduler`
  - Password: `scheduler`
  - Host from your laptop: `localhost:5432`
  - Host from other containers (docker network): `postgres:5432`
- **pgAdmin**
  - URL: http://localhost:5050
  - Login: `admin@scheduler.com`
  - Password: `admin`
- **Grafana**
  - URL: http://localhost:3000
  - Login: `admin`
  - Password: `admin`

### Application services (docker-compose.app.yml)


| Scheduler API    | `scheduler-api`    | http://localhost:8080 | REST ingestion + workflow defs + execution control |
| Scheduler Master | `scheduler-master` | (no host port) | Consumes commands WAL; schedules triggers; materializes DAG; publishes ready tasks; consumes task state |
| Scheduler Worker | `scheduler-worker` | (no host port) | Consumes ready tasks; executes HTTP/SCRIPT; publishes task state |

API endpoints you will use most:
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

---

## Bring everything up

From repository root:

```bash
# 1) infra
docker compose -f demo/docker-compose.infra.yml up -d

# (optional but useful) wait until flyway completed migrations
docker logs -f scheduler-flyway

# 2) app
docker compose -f demo/docker-compose.app.yml up -d --build

docker compose -f demo/docker-compose.app.yml up  --build
```

Verify containers:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

---

## Connect to UIs (pgAdmin, Kafka UI, Prometheus, Grafana)

### pgAdmin (connect to Postgres)

1. Open: http://localhost:5050  
   Login: `admin@scheduler.com` / `admin`
2. Add a server:
   - **Name:** `scheduler-local`
   - **Host name/address:** `postgres` *(important: this is the container DNS name on the docker network)*
   - **Port:** `5432`
   - **Maintenance database:** `scheduler`
   - **Username:** `scheduler`
   - **Password:** `scheduler`
3. After connecting, open Query Tool and run:

```sql
select now();
select count(*) from t_command;
select count(*) from t_workflow_instance;
select count(*) from t_task_instance;
```

### Kafka UI

1. Open: http://localhost:8088  
2. Cluster `local` is preconfigured to `kafka:29092` (internal docker listener).
3. Useful places:
   - **Topics** → pick a topic → **Messages** to see payloads
   - **Consumers** → observe consumer group lag (master/worker)

Topics created on infra startup (`kafka-init` container):
- `scheduler.commands.v1` (API → Master)
- `scheduler.tasks.ready.v1` (Master → Worker)
- `scheduler.task.state.v1` (Worker → Master)
- `scheduler.alerts.v1` (alerts)

### Kafka CLI (handy commands)

These run against the Kafka container:

```bash
# List topics (internal docker listener)
docker exec -it scheduler-kafka bash -lc "kafka-topics --bootstrap-server kafka:29092 --list"

# Consume 5 messages from WAL (from beginning)
docker exec -it scheduler-kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:29092 --topic scheduler.commands.v1 --from-beginning --max-messages 5"

# Consume 5 messages from ready tasks
docker exec -it scheduler-kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:29092 --topic scheduler.tasks.ready.v1 --from-beginning --max-messages 5"

# Consume 5 messages from task state
docker exec -it scheduler-kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:29092 --topic scheduler.task.state.v1 --from-beginning --max-messages 5"
```

### Prometheus

Open: http://localhost:9090

1. Check scrape targets: **Status → Targets**
2. Try queries:
   - `up`
   - `rate(scheduler_command_publish_total[1m])`
   - `rate(scheduler_master_command_kafka_consumed_total[1m])`
   - `rate(scheduler_master_ready_published_total[1m])`
   - `rate(scheduler_worker_task_success_count_total[1m])`
   - `rate(scheduler_worker_task_fail_count_total[1m])`

### Grafana

Open: http://localhost:3000 (admin/admin)

1. **Connections → Data sources**: Prometheus is provisioned.
2. **Dashboards**: A demo dashboard is provisioned from `demo/grafana/*`.
3. After running workflows, you should see activity in ingestion/master/worker panels.

---

## Run an end-to-end verification

### Step 0 — confirm API is healthy

```bash
curl -fsS http://localhost:8080/actuator/health
```

### Step 1 — Create 2 workflow definitions (HTTP + SCRIPT)

HTTP-only:

```bash
curl -sS -X POST "http://localhost:8080/scheduler/workflow-definitions/create" \
  -H "Content-Type: application/json" \
  -d @- <<'JSON'
{
  "tenantId": "default",
  "name": "demo_http_only",
  "workflowCode": null,
  "taskDefinitionJson": "[{\"name\":\"HTTP_1\",\"taskType\":\"HTTP\",\"definition\":{\"method\":\"GET\",\"url\":\"https://postman-echo.com/get?hello=world\",\"timeoutMs\":10000,\"headers\":{\"accept\":\"application/json\"}}}]",
  "taskRelationJson": "[]"
}
JSON
```

SCRIPT-only:

```bash
curl -sS -X POST "http://localhost:8080/scheduler/workflow-definitions/create" \
  -H "Content-Type: application/json" \
  -d @- <<'JSON'
{
  "tenantId": "default",
  "name": "demo_script_only",
  "workflowCode": null,
  "taskDefinitionJson": "[{\"name\":\"SCRIPT_1\",\"taskType\":\"SCRIPT\",\"definition\":{\"inline\":\"#!/usr/bin/env bash\\necho 'hello from script'\\nexit 0\\n\",\"timeoutMs\":30000,\"maxOutputBytes\":16384,\"env\":{\"DEMO\":\"1\"}}}]",
  "taskRelationJson": "[]"
}
JSON
```

✅ Copy the returned `workflowCode` values. You will use them below.

### Step 2 — Start workflow instances (scheduled “now”)

Endpoint:  
`POST /scheduler/projects/{projectCode}/executors/start-process-instance`

HTTP instance example:

```bash
curl -sS -X POST "http://localhost:8080/scheduler/projects/1/executors/start-process-instance" \
  -H "Content-Type: application/json" \
  -H "traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01" \
  -d @- <<JSON
{
  "processDefinitionCode": <HTTP_WORKFLOW_CODE>,
  "scheduleTime": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "execType": "START_PROCESS",
  "failureStrategy": "END",
  "warningType": "NONE",
  "warningGroupId": 0,
  "workerGroup": "default",
  "environmentCode": 0,
  "runMode": "RUN_PARALLEL",
  "startNodeList": "[]",
  "taskDependType": "TASK_ONLY",
  "idempotencyKey": "demo-http-001"
}
JSON
```

SCRIPT instance: same payload, just replace the code and idempotencyKey.

### Step 3 — Verify pipeline (one clear path)

#### A) API → Kafka WAL

Kafka UI → `scheduler.commands.v1` → Messages  
✅ You should see a command produced after calling `start-process-instance`.

#### B) Master → Postgres persistence

In pgAdmin Query Tool:

```sql
-- commands
select * from t_command order by created_at desc limit 5;

-- idempotency dedupe
select * from t_command_dedupe order by processed_at desc limit 5;
```

Expected:
- `t_command` has at least one row per ingest request
- `t_command_dedupe` records processed commands (idempotency)

#### C) Master scheduling → triggers + workflow instance

```sql
select * from t_workflow_instance order by workflow_instance_id desc limit 5;
select * from t_trigger order by trigger_id desc limit 10;
```

Expected:
- Workflow instance row exists; `schedule_time` matches the request (or normalized to now if in the past)
- Trigger row exists with `due_time = schedule_time`
- Trigger progresses to `DONE` after processing

#### D) DAG materialization → tasks created

```sql
select task_instance_id, workflow_instance_id, task_code, attempt, status, created_at
from t_task_instance
order by task_instance_id desc
limit 20;
```

Expected:
- At least one task row for each started workflow
- Status progresses to terminal (`SUCCESS` or `FAILED`)

#### E) Master → Kafka ready queue

Kafka UI → `scheduler.tasks.ready.v1`  
✅ A message appears when a task becomes runnable.

#### F) Worker execution → Kafka state events

Kafka UI → `scheduler.task.state.v1`  
✅ You should see `STARTED` and a terminal event for the task.

#### G) Master consumes state → workflow completes

```sql
select workflow_instance_id, status, created_at, updated_at
from t_workflow_instance
order by workflow_instance_id desc
limit 10;
```

Expected:
- For single-task workflows, status should reach a terminal state quickly.

---

## Smoke test script

A ready-to-run smoke script is included:

- `demo/smoke.sh`

Run it from repo root:

```bash
bash demo/smoke.sh
```

If you want the script to also bring up docker compose first:

```bash
bash demo/smoke.sh up
```

Environment overrides:
- `BASE_URL` (default `http://localhost:8080`)
- `PROJECT_CODE` (default `1`)

---

## Tear down & cleanup

### Normal stop (keep volumes)

```bash
docker compose -f demo/docker-compose.app.yml down
docker compose -f demo/docker-compose.infra.yml down
```

### Hard cleanup (remove volumes + local images created by compose)

```bash
docker compose -f demo/docker-compose.app.yml down -v --rmi local
docker compose -f demo/docker-compose.infra.yml down -v --rmi local
```

### Useful purge commands (be careful)

```bash
# Remove ALL stopped containers
docker container prune -f

# Remove dangling images
docker image prune -f

# Remove unused volumes (this deletes persisted Postgres/Kafka data!)
docker volume prune -f

# Remove unused networks
docker network prune -f
```

---

## Postman collection

A Postman collection is included at:

- `demo/postman/scheduler-platform-demo.postman_collection.json`

It contains:
- Health check
- Create HTTP workflow definition
- Create SCRIPT workflow definition
- Start process instance
- List process instances
- Get workflow instance
- List tasks for workflow instance

Import it into Postman and set variables:
- `baseUrl = http://localhost:8080`
- `projectCode = 1`

---

## Troubleshooting

### Check logs

```bash
docker logs -f scheduler-api
docker logs -f scheduler-master
docker logs -f scheduler-worker
```

### Common issues

- **Flyway didn’t run / DB tables missing**  
  Check: `docker logs scheduler-flyway`

- **Kafka topics missing**  
  Check: `docker logs scheduler-kafka-init` (topics are created there)

- **App containers fail because OTel agent jar is missing**  
  `demo/docker-compose.app.yml` mounts:
  `demo/otel/opentelemetry-javaagent.jar`  
  Place the file before starting the app compose.
