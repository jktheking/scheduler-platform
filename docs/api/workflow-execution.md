# Workflow Execution APIs

Base: `/scheduler/projects/{projectCode}/executors`

## Start process instance

`POST/start-process-instance`

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

Response (this implementation returns `processInstanceId` in `data`):

```json
{"code":0,"msg":"success","data":{"id":10001,"processDefinitionCode":900001,"state":"SUBMITTED"},"success":true,"failed":false}
```
