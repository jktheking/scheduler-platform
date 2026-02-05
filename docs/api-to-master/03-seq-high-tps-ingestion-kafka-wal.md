# Sequence Diagram — High TPS Ingestion (Kafka WAL + Writer Group)

This sequence diagram shows the **high TPS ingestion path** where `scheduler-api`
writes the command to Kafka as a **WAL** (`scheduler.commands.v1`), and a separate
**writer consumer group** persists the command into Postgres (`t_command`).

```mermaid
sequenceDiagram
  autonumber

  participant UI as Client
  participant API as SchedulerAPI
  participant UC as StartWorkflowUseCase
  participant ADM as AdmissionStack
  participant KGW as KafkaWalIngestionGateway
  participant KBUS as KafkaCommandBus
  participant K as KafkaCommandsTopic
  participant WR as MasterWriter
  participant DB as Postgres

  UI->>API: POST start-process-instance
  API->>UC: execute request
  UC->>ADM: admit CommandEnvelope

  Note over ADM: TokenBucket<br/>InflightLimiter<br/>CircuitBreaker<br/>KafkaPressureSampler<br/>KafkaAwareAdmissionController

  ADM-->>UC: ALLOW
  UC->>KGW: ingest CommandEnvelope
  KGW->>KBUS: publish CommandEnvelope
  KBUS->>K: produce command to scheduler.commands.v1
  K-->>KBUS: ack
  KBUS-->>KGW: ok
  KGW-->>UC: accepted
  UC-->>API: accepted WAL
  API-->>UI: HTTP 200 OK

  Note over WR,K: Writer consumer group persists WAL to DB

  WR->>K: poll scheduler.commands.v1
  K-->>WR: CommandEnvelope
  WR->>DB: insert into t_command (idempotent)
  DB-->>WR: ok or duplicate
