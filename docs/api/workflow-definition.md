# Workflow Definition APIs

Base: `/scheduler/projects/{projectCode}/process-definition`

## Create workflow definition

`POST/create`

Example body:

```json
{
 "projectCode": 123456789,
 "name": "daily_etl",
 "description": "daily etl pipeline",
 "locations": "{\"111\":{\"x\":100,\"y\":200}}",
 "globalParams": "[{\"prop\":\"bizDate\",\"value\":\"${system.biz.date}\"}]",
 "taskDefinitionJson": "[{\"code\":111,\"name\":\"t1\",\"type\":\"SHELL\",\"params\":\"{...}\"}]",
 "taskRelationJson": "[{\"preTaskCode\":0,\"postTaskCode\":111}]",
 "tenantCode": "default"
}
```

Response envelope:

```json
{"code":0,"msg":"success","data":{"code":900001,"version":1,"name":"daily_etl","releaseState":"OFFLINE"},"success":true,"failed":false}
```
