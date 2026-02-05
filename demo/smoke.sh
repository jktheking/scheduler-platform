#!/usr/bin/env bash
set -euo pipefail

# Scheduler Platform demo smoke test (local docker compose)
# - Creates 2 workflows (HTTP + SCRIPT)
# - Starts 2 workflow instances
# - Verifies Kafka topics have messages
# - Verifies Postgres tables are populated and statuses progress
#
# Requirements:
#   - bash (Git Bash on Windows is fine)
#   - docker compose v2
#   - curl
# Optional:
#   - jq (prettier JSON parsing; script falls back to python if jq missing)

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO_DIR="$ROOT_DIR/demo"

INFRA_COMPOSE="$DEMO_DIR/docker-compose.infra.yml"
APP_COMPOSE="$DEMO_DIR/docker-compose.app.yml"

BASE_URL="${BASE_URL:-http://localhost:8080}"
PROJECT_CODE="${PROJECT_CODE:-1}"

HTTP_WORKFLOW_NAME="${HTTP_WORKFLOW_NAME:-demo_http_only}"
SCRIPT_WORKFLOW_NAME="${SCRIPT_WORKFLOW_NAME:-demo_script_only}"

IDEMP_HTTP="${IDEMP_HTTP:-demo-http-001}"
IDEMP_SCRIPT="${IDEMP_SCRIPT:-demo-script-001}"

wait_http () {
  local url="$1"
  local name="$2"
  local timeout="${3:-120}"
  echo "==> Waiting for $name: $url (timeout ${timeout}s)"
  local start
  start="$(date +%s)"
  while true; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "    OK: $name is reachable"
      break
    fi
    local now
    now="$(date +%s)"
    if (( now - start > timeout )); then
      echo "ERROR: timed out waiting for $name ($url)" >&2
      exit 1
    fi
    sleep 2
  done
}

json_get () {
  # json_get '<json>' '<jqExpr>' - prints value (no quotes)
  local json="$1"
  local expr="$2"
  if command -v jq >/dev/null 2>&1; then
    echo "$json" | jq -r "$expr"
  else
    python - <<PY
import json,sys
obj=json.loads(sys.stdin.read())
# tiny jq-ish: only supports top-level field lookup like .workflowCode
key="${expr}".lstrip(".")
print(obj.get(key,""))
PY
  fi
}

compose_up () {
  echo "==> Bringing up infra"
  docker compose -f "$INFRA_COMPOSE" up -d
  echo "==> Waiting for Flyway to complete migrations"
  # Flyway container exits after finishing; so we tail until it stops.
  docker logs -f scheduler-flyway || true

  echo "==> Bringing up app"
  if [[ ! -f "$DEMO_DIR/otel/opentelemetry-javaagent.jar" ]]; then
    echo "WARN: demo/otel/opentelemetry-javaagent.jar not found. App containers will likely fail to start."
    echo "      Place the OpenTelemetry javaagent jar at demo/otel/opentelemetry-javaagent.jar"
  fi
  docker compose -f "$APP_COMPOSE" up -d --build
}

verify_kafka_has_messages () {
  local topic="$1"
  echo "==> Verifying Kafka topic has messages: $topic"
  # We read up to 1 message (timeout 8s). If none, kafka-console-consumer exits non-zero.
  docker exec -i scheduler-kafka bash -lc \
    "kafka-console-consumer --bootstrap-server kafka:29092 --topic '$topic' --from-beginning --max-messages 1 --timeout-ms 8000" \
    >/dev/null
  echo "    OK: $topic has messages"
}

psql_query () {
  local sql="$1"
  docker exec -i scheduler-postgres bash -lc \
    "psql -U scheduler -d scheduler -v ON_ERROR_STOP=1 -Atc \"$sql\""
}

verify_db_nonempty () {
  local label="$1"
  local sql="$2"
  echo "==> DB check: $label"
  local out
  out="$(psql_query "$sql" || true)"
  if [[ -z "$out" ]]; then
    echo "ERROR: DB check failed (no rows): $label" >&2
    echo "SQL: $sql" >&2
    exit 1
  fi
  echo "    OK: $label"
}

create_workflow_http () {
  echo "==> Creating HTTP workflow definition ($HTTP_WORKFLOW_NAME)"
  local payload
  payload="$(cat <<'JSON'
{
  "tenantId": "default",
  "name": "__NAME__",
  "workflowCode": null,
  "taskDefinitionJson": "[{\"name\":\"HTTP_1\",\"taskType\":\"HTTP\",\"definition\":{\"method\":\"GET\",\"url\":\"https://postman-echo.com/get?hello=world\",\"timeoutMs\":10000,\"headers\":{\"accept\":\"application/json\"}}}]",
  "taskRelationJson": "[]"
}
JSON
)"
  payload="${payload/__NAME__/$HTTP_WORKFLOW_NAME}"
  local resp
  resp="$(curl -fsS -X POST "$BASE_URL/scheduler/workflow-definitions/create" -H "Content-Type: application/json" -d "$payload")"
  echo "    Response: $resp"
  local code
  code="$(json_get "$resp" ".workflowCode")"
  if [[ -z "$code" || "$code" == "null" ]]; then
    echo "ERROR: could not extract workflowCode from response" >&2
    exit 1
  fi
  echo "$code"
}

create_workflow_script () {
  echo "==> Creating SCRIPT workflow definition ($SCRIPT_WORKFLOW_NAME)"
  # inline bash script is encoded with \n
  local payload
  payload="$(cat <<'JSON'
{
  "tenantId": "default",
  "name": "__NAME__",
  "workflowCode": null,
  "taskDefinitionJson": "[{\"name\":\"SCRIPT_1\",\"taskType\":\"SCRIPT\",\"definition\":{\"inline\":\"#!/usr/bin/env bash\\necho 'hello from script'\\nexit 0\\n\",\"timeoutMs\":30000,\"maxOutputBytes\":16384,\"env\":{\"DEMO\":\"1\"}}}]",
  "taskRelationJson": "[]"
}
JSON
)"
  payload="${payload/__NAME__/$SCRIPT_WORKFLOW_NAME}"
  local resp
  resp="$(curl -fsS -X POST "$BASE_URL/scheduler/workflow-definitions/create" -H "Content-Type: application/json" -d "$payload")"
  echo "    Response: $resp"
  local code
  code="$(json_get "$resp" ".workflowCode")"
  if [[ -z "$code" || "$code" == "null" ]]; then
    echo "ERROR: could not extract workflowCode from response" >&2
    exit 1
  fi
  echo "$code"
}

start_instance () {
  local workflow_code="$1"
  local idem="$2"
  echo "==> Starting workflow instance code=$workflow_code idem=$idem"
  # Use current time in UTC; master normalizes past times to now.
  local now_utc
  now_utc="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  local payload
  payload="$(cat <<JSON
{
  "processDefinitionCode": $workflow_code,
  "scheduleTime": "$now_utc",
  "execType": "START_PROCESS",
  "failureStrategy": "END",
  "warningType": "NONE",
  "warningGroupId": 0,
  "workerGroup": "default",
  "environmentCode": 0,
  "runMode": "RUN_PARALLEL",
  "startNodeList": "[]",
  "taskDependType": "TASK_ONLY",
  "idempotencyKey": "$idem"
}
JSON
)"
  local resp
  resp="$(curl -fsS -X POST "$BASE_URL/scheduler/projects/$PROJECT_CODE/executors/start-process-instance" \
    -H "Content-Type: application/json" \
    -H "traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01" \
    -d "$payload")"
  echo "    Response: $resp"
}

main () {
  if [[ "${1:-}" == "up" ]]; then
    compose_up
  fi

  wait_http "$BASE_URL/actuator/health" "scheduler-api health" 180

  # Create definitions
  local http_code script_code
  http_code="$(create_workflow_http)"
  script_code="$(create_workflow_script)"
  echo "HTTP workflowCode:   $http_code"
  echo "SCRIPT workflowCode: $script_code"

  # Start instances
  start_instance "$http_code" "$IDEMP_HTTP"
  start_instance "$script_code" "$IDEMP_SCRIPT"

  echo "==> Waiting a bit for master/worker to process..."
  sleep 8

  # Kafka checks
  verify_kafka_has_messages "scheduler.commands.v1"
  verify_kafka_has_messages "scheduler.tasks.ready.v1"
  verify_kafka_has_messages "scheduler.task.state.v1"

  # DB checks (minimal but meaningful)
  verify_db_nonempty "t_command has rows" "select 1 from t_command limit 1;"
  verify_db_nonempty "t_command_dedupe has rows" "select 1 from t_command_dedupe limit 1;"
  verify_db_nonempty "t_workflow_instance has rows" "select 1 from t_workflow_instance limit 1;"
  verify_db_nonempty "t_task_instance has rows" "select 1 from t_task_instance limit 1;"

  echo "==> Latest workflow instances:"
  psql_query "select workflow_instance_id,status,created_at,updated_at from t_workflow_instance order by workflow_instance_id desc limit 5;"

  echo ""
  echo "✅ Smoke test completed."
  echo "Next: open Kafka UI (http://localhost:8088), pgAdmin (http://localhost:5050), Grafana (http://localhost:3000) to inspect details."
}

main "$@"
