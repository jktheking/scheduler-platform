# Workflow Execution APIs

Base: `/api/v1/projects/{projectCode}/workflow-instances`

## Submit a workflow instance (ingestion)

`POST /`

Headers:

- `X-Tenant-Id: <tenant>` (optional for demo; if absent, the server currently defaults tenantId to the projectCode)

Example body:

```json
{
 "processDefinitionCode": 900001,
 "scheduleTime": "2026-02-01T00:00:00Z",
 "execType": "START_PROCESS",
 "failureStrategy": "END",
 "warningType": "NONE",
 "warningGroupId": 0,
 "workerGroup": "default",
 "environmentCode": 0,
 "runMode": "RUN_PARALLEL",
 "startNodeList": "[111]",
 "taskDependType": "TASK_ONLY",
 "idempotencyKey": "req-01HXYZ"
}
```

Response (ingestion is asynchronous; master creates the real workflow instance later):

```json
{"code":0,"msg":"success","data":{"id":10001,"processDefinitionCode":900001,"state":"SUBMITTED"},"success":true,"failed":false}
```
