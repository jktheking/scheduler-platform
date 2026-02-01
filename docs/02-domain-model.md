# Domain Model

This domain model enforces **Clean Architecture** boundaries:
- Domain code has **no** dependency on Spring, messaging libraries, registries, storage SDKs, etc.
- It defines the core scheduling concepts: **definitions vs instances**, **state machines**, and **DAG** planning structures.

## Definitions vs Instances

- **Definitions** are immutable templates, identified by `(code, version)`:
 - `WorkflowDefinition(code, version, ...)`
 - `TaskDefinition(code, version, ...)`
- **Instances** are runtime executions bound to a specific definition version:
 - `WorkflowInstance(id, workflowCode, workflowVersion, ...)`
 - `TaskInstance(id, workflowInstanceId, taskCode, taskVersion, ...)`

## Key entities (class diagram)

```mermaid
classDiagram
 class WorkflowDefinition {
 +WorkflowCode code
 +int version
 +String name
 +TenantId tenantId
 +ProjectId projectId
 +List~Parameter~ globalParams
 +String locationsJson
 }

 class TaskDefinition {
 +TaskCode code
 +int version
 +String name
 +String taskType
 +String taskParamsJson
 +Duration timeout
 +int maxRetryTimes
 +Duration retryInterval
 }

 class WorkflowTaskRelation {
 +WorkflowCode workflowCode
 +int workflowVersion
 +TaskCode preTaskCode
 +TaskCode postTaskCode
 }

 class WorkflowInstance {
 +WorkflowInstanceId id
 +WorkflowCode workflowCode
 +int workflowVersion
 +WorkflowExecutionStatus state
 +CommandType commandType
 }

 class TaskInstance {
 +TaskInstanceId id
 +WorkflowInstanceId workflowInstanceId
 +TaskCode taskCode
 +int taskVersion
 +TaskExecutionStatus state
 +int attempt
 }

 WorkflowDefinition --> WorkflowTaskRelation : "has edges"
 WorkflowDefinition --> TaskDefinition : "references tasks"
 WorkflowInstance --> WorkflowDefinition : "executes version"
 TaskInstance --> TaskDefinition : "executes version"
 TaskInstance --> WorkflowInstance : "belongs to"
```

## Workflow state machine

```mermaid
stateDiagram-v2
 [*] --> SUBMITTED
 SUBMITTED --> RUNNING
 SUBMITTED --> KILL
 RUNNING --> PAUSED
 PAUSED --> RUNNING
 RUNNING --> SUCCESS
 RUNNING --> FAILURE
 RUNNING --> KILL
 PAUSED --> KILL
 SUCCESS --> [*]
 FAILURE --> [*]
 KILL --> [*]
```

## Task state machine

```mermaid
stateDiagram-v2
 [*] --> SUBMITTED_SUCCESS
 SUBMITTED_SUCCESS --> DISPATCH
 SUBMITTED_SUCCESS --> KILL
 DISPATCH --> RUNNING_EXECUTION
 DISPATCH --> NEED_FAULT_TOLERANCE
 DISPATCH --> KILL
 RUNNING_EXECUTION --> SUCCESS
 RUNNING_EXECUTION --> FAILURE
 RUNNING_EXECUTION --> NEED_FAULT_TOLERANCE
 RUNNING_EXECUTION --> KILL
 NEED_FAULT_TOLERANCE --> SUBMITTED_SUCCESS
 NEED_FAULT_TOLERANCE --> KILL
 SUCCESS --> [*]
 FAILURE --> [*]
 KILL --> [*]
```

## DAG model

The domain includes a simple `Dag` structure:
- nodes: task codes (long)
- edges: dependency links (pre → post)
- provides `topologicalOrder()` with cycle detection

Cycle detection failure throws `DagCycleException`.
