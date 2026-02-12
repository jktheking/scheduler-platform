# Sequence Diagram — High TPS Ingestion (Kafka WAL + DB Command Writer)

This is the **ingestion path** when `SCHEDULER_INGESTION_MODE=KAFKA`.

- `scheduler-api` publishes `CommandEnvelope` to Kafka WAL topic `scheduler.commands.v1`.
- `KafkaCommandWriterLoop` (writer consumer group) persists the raw command to Postgres `t_command`
  via `insertIfAbsent` (idempotent).

```mermaid
sequenceDiagram
  autonumber

  participant C as Client
  participant API as scheduler-api
  participant UC as StartWorkflowUseCase
  participant AC as AdmissionController
  participant CB as MeteredCommandBus
  participant K as Kafka (scheduler.commands.v1)
  participant WR as KafkaCommandWriterLoop
  participant DB as Postgres (t_command)

  C->>API: POST /workflow-instances (start)
  API->>UC: execute(request)
  UC->>AC: decide(tenant, "workflow.start")
  alt rejected
    AC-->>UC: REJECT
    UC-->>API: 429 Too Many Requests
    API-->>C: rejected
  else accepted
    AC-->>UC: ACCEPT
    UC->>CB: publish(CommandEnvelope)
    CB->>K: produce(CommandEnvelope)
    K-->>CB: ack
    CB-->>UC: ok
    UC-->>API: SUBMITTED
    API-->>C: 202 SUBMITTED
  end

  WR->>K: poll()
  K-->>WR: CommandEnvelope
  WR->>DB: insertIfAbsent(command_id, payload_json, ...)
  DB-->>WR: ok (or conflict no-op)
```
