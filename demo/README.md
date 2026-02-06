# Scheduler Platform Demo (Local Docker)

This demo runs the full end-to-end pipeline locally:

**API → Kafka (WAL commands) → Master (persist + schedule + DAG) → Kafka (ready tasks) → Worker (exec HTTP/SCRIPT) → Kafka (task state) → Master (advance DAG) → Postgres + Metrics (Prometheus/Grafana)**

The API follows REST-style paths under **`/api/v1`** and uses **tenant routing via header**:

- `X-Tenant-Id: default` (required for workflow definition APIs; accepted for others)

---

## Quick index

- [Prerequisites](#prerequisites)
- [Services and ports](#services-and-ports)
- [Bring everything up](#bring-everything-up)
- [Demo walkthrough](#demo-walkthrough)
- [Validate via Postgres](#validate-via-postgres)
- [Validate via Kafka UI and Kafka CLI](#validate-via-kafka-ui-and-kafka-cli)
- [Validate via Prometheus](#validate-via-prometheus)
- [Validate via Grafana](#validate-via-grafana)
- [Postman collection](#postman-collection)
- [Troubleshooting and debugging](#troubleshooting-and-debugging)
- [Tear down and cleanup](#tear-down-and-cleanup)

---

## Prerequisites

- Docker Desktop (or Docker Engine) with **Docker Compose v2**
- `curl`
- Optional but recommended: `jq`

---

## Services and ports

> Compose files:
> - `demo/docker-compose.infra.yml` (infra + observability)
> - `demo/docker-compose.app.yml` (application services)

### Infra + Observability (docker-compose.infra.yml)

| Service | Container | Host URL / Port | Purpose | Credentials / Notes |
|---|---|---:|---|---|
| Postgres 16 | `scheduler-postgres` | `localhost:5432` | Metadata store | DB=`scheduler`, user=`scheduler`, pass=`scheduler` |
| pgAdmin | `scheduler-pgadmin` | http://localhost:5050 | Postgres UI | login=`admin@scheduler.com`, pass=`admin` |
| Kafka (KRaft) | `scheduler-kafka` | `localhost:9092` | WAL + ready + state topics | Internal docker listener: `kafka:29092` |
| Kafka UI | `scheduler-kafka-ui` | http://localhost:8088 | Browse topics/messages/groups | Cluster `local` preconfigured |
| Flyway | `scheduler-flyway` | (no UI) | Runs DB migrations at startup | Check logs: `docker logs -f scheduler-flyway` |
| OTel Collector | `scheduler-otel-collector` | `localhost:4317` / `localhost:4318` | OTLP ingest (traces/metrics) | Exposes Prometheus exporter for Prometheus scrape |
| Prometheus | `scheduler-prometheus` | http://localhost:9090 | Metrics store/query | `Status → Targets` should show OTel collector |
| Grafana | `scheduler-grafana` | http://localhost:3000 | Dashboards | login=`admin`, pass=`admin` |

### Application (docker-compose.app.yml)

| Service | Container | Host URL / Port | Purpose |
|---|---|---:|---|
| Scheduler API | `scheduler-api` | http://localhost:8080 | REST ingestion + defs + query |
| Scheduler Master | `scheduler-master` | (no host port) | Consumes WAL; schedules triggers; owns DAG runtime; publishes ready tasks; consumes task state |
| Scheduler Worker | `scheduler-worker` | (no host port) | Consumes ready tasks; executes HTTP/SCRIPT; publishes task state |

API entry points:
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

---

## Bring everything up

From repository root:

```bash
# 1) Infra
docker compose -f demo/docker-compose.infra.yml up -d

# Wait for Flyway to complete
docker logs -f scheduler-flyway

# 2) App (build + run)
docker compose -f demo/docker-compose.app.yml up -d --build
```

Verify containers:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

---

## Demo walkthrough

### Step 0 — confirm API is healthy

```bash
curl -fsS http://localhost:8080/actuator/health
```

### Step 1 — Create workflow definitions

This creates 2 small workflows to prove the runtime:

- **HTTP-only**: one HTTP task
- **SCRIPT-only**: one SCRIPT task

HTTP-only:

```bash
curl -sS -X POST "http://localhost:8080/api/v1/workflow-definitions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: default" \
  -d @- <<'JSON'
{
  "name": "demo_http_only",
  "workflowCode": null,
  "taskDefinitionJson": "[{\"name\":\"HTTP_1\",\"taskType\":\"HTTP\",\"definition\":{\"method\":\"GET\",\"url\":\"https://postman-echo.com/get?hello=world\",\"timeoutMs\":10000,\"headers\":{\"accept\":\"application/json\"}}}]",
  "taskRelationJson": "[]"
}
JSON
```

SCRIPT-only:

```bash
curl -sS -X POST "http://localhost:8080/api/v1/workflow-definitions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: default" \
  -d @- <<'JSON'
{
  "name": "demo_script_only",
  "workflowCode": null,
  "taskDefinitionJson": "[{\"name\":\"SCRIPT_1\",\"taskType\":\"SCRIPT\",\"definition\":{\"inline\":\"#!/usr/bin/env bash\\necho 'hello from script'\\nexit 0\\n\",\"timeoutMs\":30000,\"maxOutputBytes\":16384,\"env\":{\"DEMO\":\"1\"}}}]",
  "taskRelationJson": "[]"
}
JSON
```

The response returns a `workflowCode` — save it (you will use it in Step 2).

### Step 2 — Start workflow instance (ingestion to Kafka WAL)

```bash
NOW="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

curl -sS -X POST "http://localhost:8080/api/v1/projects/1/workflow-instances" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: default" \
  -H "traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01" \
  -d @- <<JSON
{
  "processDefinitionCode": <workflowCode>,
  "scheduleTime": "$NOW",
  "execType": "START_PROCESS",
  "failureStrategy": "END",
  "warningType": "NONE",
  "warningGroupId": 0,
  "workerGroup": "default",
  "environmentCode": 0,
  "runMode": "RUN_PARALLEL",
  "startNodeList": "[]",
  "taskDependType": "TASK_ONLY",
  "idempotencyKey": "demo-start-001"
}
JSON
```

> Important: the ingestion endpoint is **asynchronous**. The API accepts the command; the **master** creates the actual workflow instance later.
>
> To find the `workflow_instance_id`, use the DB validation queries below.

### Step 3 — Run the scripted end-to-end DAG demo

This repo ships scripts that create a larger DAG and start it:

- `demo/e2e.sh` (A fan-out/fan-in DAG)
- `demo/smoke.sh` (creates 2 workflows + starts 2 instances + validates Kafka/DB)

Run:

```bash
bash demo/e2e.sh
# or
bash demo/smoke.sh
```

Both scripts use `/api/v1/...` and set `X-Tenant-Id` correctly.

---

## Validate via Postgres

Use either pgAdmin or psql.

### pgAdmin connection (recommended for UI)

1. Open: http://localhost:5050  
   Login: `admin@scheduler.com` / `admin`
2. Add a server:
   - **Name:** `scheduler-local`
   - **Host name/address:** `postgres` (docker DNS name)
   - **Port:** `5432`
   - **Maintenance database:** `scheduler`
   - **Username:** `scheduler`
   - **Password:** `scheduler`

### “What happened?” queries (copy/paste)

**Latest commands (API → master):**

```sql
select id, tenant_id, workflow_code, command_type, status, created_at, last_error_message
from t_command
order by id desc
limit 20;
```

**Latest workflow instances (master-owned):**

```sql
select id, tenant_id, workflow_code, workflow_version, status, created_at, updated_at, last_error
from t_workflow_instance
order by id desc
limit 10;
```

**Latest triggers (scheduling + retries):**

```sql
select id, workflow_instance_id, status, due_time, claimed_by, last_error
from t_trigger
order by id desc
limit 20;
```

**Task instances (worker execution plane):**

```sql
select id, workflow_instance_id, task_code, task_name, status, attempt, claimed_by, started_at, finished_at, last_error
from t_task_instance
order by id desc
limit 50;
```

**Find the most recent workflow instance id to use in API queries:**

```sql
select id
from t_workflow_instance
order by id desc
limit 1;
```

---

## Validate via Kafka UI and Kafka CLI

### Kafka UI (recommended)

1. Open: http://localhost:8088
2. Click **Topics** and inspect:
   - `scheduler.commands.v1` (API → master)
   - `scheduler.tasks.ready.v1` (master → worker)
   - `scheduler.task.state.v1` (worker → master)
3. Check consumer groups:
   - master group consuming commands
   - worker group consuming ready tasks
   - master group consuming task state

### Kafka CLI (handy copy/paste)

```bash
docker exec -it scheduler-kafka bash -lc "kafka-topics --bootstrap-server kafka:29092 --list"

docker exec -it scheduler-kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:29092 --topic scheduler.commands.v1 --from-beginning --max-messages 5"
docker exec -it scheduler-kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:29092 --topic scheduler.tasks.ready.v1 --from-beginning --max-messages 5"
docker exec -it scheduler-kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:29092 --topic scheduler.task.state.v1 --from-beginning --max-messages 5"
```

---

## Validate via Prometheus

Open: http://localhost:9090

1. **Status → Targets**: ensure scrape targets are UP
2. Run these example queries:

- Service liveness:
  - `up`
- Ingestion:
  - `rate(scheduler_command_publish_total[1m])`
  - `rate(scheduler_master_command_kafka_consumed_total[1m])`
- Master scheduling:
  - `rate(scheduler_master_trigger_due_total[1m])`
  - `rate(scheduler_master_ready_published_total[1m])`
- Worker:
  - `rate(scheduler_worker_task_started_total[1m])`
  - `rate(scheduler_worker_task_success_count_total[1m])`
  - `rate(scheduler_worker_task_fail_count_total[1m])`

> Metric names may vary slightly depending on the build. If a query returns empty, search by prefix:
> - `{__name__=~"scheduler_.*"}`

---

## Validate via Grafana

Open: http://localhost:3000 (admin/admin)

### Step-by-step dashboard navigation

1. Go to **Connections → Data sources**
   - Ensure **Prometheus** is present and healthy
2. Go to **Dashboards**
   - Open the demo dashboard provisioned from `demo/grafana/*`
3. After you run `demo/smoke.sh` or `demo/e2e.sh`, you should see:
   - ingestion rate / command publish rate
   - master consume + ready publish
   - worker execution successes/failures
   - queue delay histograms (if enabled)

### If dashboard shows “No data”
- Confirm Prometheus targets are up: http://localhost:9090/targets
- Confirm OTel collector is reachable from containers
- Look at service logs (see Troubleshooting)

---

## Postman collection

A Postman collection is included and kept aligned with the current controller signatures:

- `demo/postman/scheduler-platform-demo.postman_collection.json`

Import into Postman and run in order:

1. Health checks
2. Create workflow definition (HTTP-only)
3. Create workflow definition (DAG: A fan-out/fan-in)
4. Start workflow instance (ingestion)
5. (Manual) Fetch latest `workflow_instance_id` from Postgres and set `workflowInstanceId` variable
6. Query instance / tasks / tracking endpoints

---

## Troubleshooting and debugging

### Where to look first

**API logs**
```bash
docker logs scheduler-api --tail=200
```

**Master logs**
```bash
docker logs scheduler-master --tail=300
```

**Worker logs**
```bash
docker logs scheduler-worker --tail=300
```

### Common failure modes

#### 1) OTel exporter cannot connect
Symptom: spans export errors in logs.

Fix:
- Ensure `scheduler-otel-collector` is running
- Verify internal docker DNS name matches compose (`otel-collector`)

#### 2) Kafka not ready / topic missing
Symptom: master/worker consumer fails to start.

Fix:
- Open Kafka UI and confirm topics exist
- Check `kafka-init` logs in infra compose

#### 3) Workflow instance is `FAILED`
Use DB queries:
- `t_workflow_instance.last_error`
- `t_trigger.last_error`
- `t_task_instance.last_error`
- `t_command.last_error_message`

Then correlate with logs using identifiers:
- `workflowInstanceId`, `commandId`, `triggerId`, `taskInstanceId`

### API “tracking” endpoint
Once you have an instance id:

- `GET /api/v1/workflow-instances/{id}/tracking`

This aggregates:
- workflow status + last_error
- trigger summary + last_error
- command summary + last_error_message
- task counts by status

---

## Tear down and cleanup

```bash
docker compose -f demo/docker-compose.app.yml down -v
docker compose -f demo/docker-compose.infra.yml down -v
docker volume prune -f
```
