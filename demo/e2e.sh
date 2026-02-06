#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
PROJECT_CODE="${PROJECT_CODE:-100}"
TENANT_ID="${TENANT_ID:-default}"

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: required command '$1' not found on PATH" >&2
    exit 1
  }
}

require curl
require jq
require date

echo "API_BASE=$API_BASE PROJECT_CODE=$PROJECT_CODE TENANT_ID=$TENANT_ID"

echo
echo "1) Create workflow definition: A(SCRIPT) -> (B(HTTP), C(SCRIPT)) -> D(HTTP)"

# NOTE: ScriptTaskExecutor expects "inline" (NOT inlineScript)
TASK_DEFS=$(cat <<'JSON'
[
  {
    "name": "A",
    "taskType": "SCRIPT",
    "definition": {
      "inline": "#!/usr/bin/env bash\necho A\nexit 0\n",
      "timeoutMs": 60000,
      "maxOutputBytes": 16384
    }
  },
  {
    "name": "B",
    "taskType": "HTTP",
    "definition": {
      "method": "GET",
      "url": "https://postman-echo.com/get?from=B",
      "timeoutMs": 10000,
      "headers": { "accept": "application/json" }
    }
  },
  {
    "name": "C",
    "taskType": "SCRIPT",
    "definition": {
      "inline": "#!/usr/bin/env bash\necho C\nexit 0\n",
      "timeoutMs": 60000,
      "maxOutputBytes": 16384
    }
  },
  {
    "name": "D",
    "taskType": "HTTP",
    "definition": {
      "method": "GET",
      "url": "https://postman-echo.com/get?from=D",
      "timeoutMs": 10000,
      "headers": { "accept": "application/json" }
    }
  }
]
JSON
)

EDGES=$(cat <<'JSON'
[
  {"from":"A","to":"B"},
  {"from":"A","to":"C"},
  {"from":"B","to":"D"},
  {"from":"C","to":"D"}
]
JSON
)

CREATE_PAYLOAD=$(jq -n \
  --arg name "demo_A_fanout_fanin_D" \
  --arg taskDefinitionJson "$TASK_DEFS" \
  --arg taskRelationJson "$EDGES" \
  '{    name: $name,
    taskDefinitionJson: $taskDefinitionJson,
    taskRelationJson: $taskRelationJson
  }'
)

CREATE_RESP=$(curl -sS -X POST "$API_BASE/api/v1/workflow-definitions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" \
  -d "$CREATE_PAYLOAD")

echo "$CREATE_RESP" | jq .

WORKFLOW_CODE=$(echo "$CREATE_RESP" | jq -r '.workflowCode')
if [[ -z "$WORKFLOW_CODE" || "$WORKFLOW_CODE" == "null" ]]; then
  echo "ERROR: Could not parse workflowCode from create response" >&2
  exit 1
fi

echo
echo "✔ Created workflowCode=$WORKFLOW_CODE"

echo
echo "2) Start workflow instance via start endpoint"

# Use scheduleTime (now), and include common DS-like fields (optional but clearer)
NOW_UTC="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
IDEMPOTENCY_KEY="demo-e2e-$(date +%s)"

START_PAYLOAD=$(jq -n \
  --argjson processDefinitionCode "$WORKFLOW_CODE" \
  --arg scheduleTime "$NOW_UTC" \
  --arg idempotencyKey "$IDEMPOTENCY_KEY" \
  '{
    processDefinitionCode: $processDefinitionCode,
    scheduleTime: $scheduleTime,
    execType: "START_PROCESS",
    failureStrategy: "END",
    warningType: "NONE",
    warningGroupId: 0,
    workerGroup: "default",
    environmentCode: 0,
    runMode: "RUN_PARALLEL",
    startNodeList: "[]",
    taskDependType: "TASK_ONLY",
    idempotencyKey: $idempotencyKey
  }'
)

START_RESP=$(curl -sS -X POST "$API_BASE/api/v1/projects/$PROJECT_CODE/workflow-instances" \
  -H "Content-Type: application/json" \
  -H "traceparent: 00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01" \
  -d "$START_PAYLOAD")

echo "$START_RESP" | jq .

echo
echo "✔ Start submitted (master will materialize instance and tasks asynchronously)."

echo
echo "3) What to check (manual verification)"
echo "Kafka UI:  http://localhost:8088"
echo "  - scheduler.commands.v1"
echo "  - scheduler.tasks.ready.v1"
echo "  - scheduler.task.state.v1"
echo
echo "Postgres (pgAdmin http://localhost:5050):"
echo "  SELECT * FROM t_command ORDER BY id DESC LIMIT 10;"
echo "  SELECT * FROM t_workflow_instance ORDER BY id DESC LIMIT 5;"
echo "  SELECT * FROM t_trigger ORDER BY id DESC LIMIT 10;"
echo "  SELECT * FROM t_task_instance ORDER BY id DESC LIMIT 20;"
echo
echo "Worker logs:"
echo "  docker logs scheduler-worker --tail=200"
echo
echo "Done."