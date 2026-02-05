# Scheduler Platform — Command Ingestion (Low TPS)

This sequence diagram shows the **low TPS ingestion path** using
direct JDBC persistence into Postgres.

```mermaid
sequenceDiagram
  autonumber

  participant UI as Client
  participant API as SchedulerAPI
  participant UC as StartWorkflowUseCase
  participant ADM as AdmissionStack
  participant GW as JdbcIngestionGateway
  participant REPO as JdbcCommandRepository
  participant DB as Postgres

  UI->>API: POST start-process-instance
  API->>UC: execute request
  UC->>ADM: admit CommandEnvelope
  ADM-->>UC: ALLOW
  UC->>GW: ingest CommandEnvelope
  GW->>REPO: insertIfAbsent command
  REPO->>DB: insert into t_command
  DB-->>REPO: ok or duplicate
  REPO-->>GW: insert result
  GW-->>UC: accepted result
  UC-->>API: commandId accepted
  API-->>UI: HTTP 200 OK
