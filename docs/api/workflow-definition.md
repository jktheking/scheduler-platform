# Workflow Definition APIs

Base: `/api/v1/workflow-definitions`

## Upsert a workflow definition

Persists the workflow DAG definition (tasks + edges). This endpoint **only stores** the definition; starting execution is done separately via the workflow execution APIs.

### Endpoint

`POST /api/v1/workflow-definitions`

### Headers

- `X-Tenant-Id: <tenant>` (**required**)

### Body

The API keeps the same "JSON-as-string" payload fields used internally for task and edge arrays.

```json
{
  "name": "daily_etl",
  "workflowCode": 900001,
  "taskDefinitionJson": "[{\"code\":111,\"name\":\"A\",\"type\":\"SCRIPT\",\"params\":{}}]",
  "taskRelationJson": "[{\"preTaskCode\":0,\"postTaskCode\":111}]"
}
```

Notes:

- Set `workflowCode` when updating an existing definition. Omit it to create a new workflow code.
- `taskDefinitionJson` and `taskRelationJson` are JSON arrays encoded as strings.

### Response

```json
{
  "workflowCode": 900001,
  "workflowVersion": 1
}
```
