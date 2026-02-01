# Scheduling APIs

Base: `/scheduler/projects/{projectCode}/schedules`

## Create schedule

`POST/create`

```json
{
 "projectCode": 123456789,
 "processDefinitionCode": 900001,
 "crontab": "0 0 2 * * ?",
 "timezoneId": "Asia/Kolkata",
 "startTime": "2026-02-01T00:00:00Z",
 "endTime": "2027-02-01T00:00:00Z",
 "failureStrategy": "END",
 "warningType": "NONE",
 "warningGroupId": 0,
 "workerGroup": "default",
 "environmentCode": 0
}
```
