# Flow — API → Kafka WAL → Master Materialization (High-Level)

```mermaid
flowchart TB
  C[Client] -->|HTTP start workflow| API[scheduler-api]
  API -->|build CommandEnvelope + AdmissionControl| SVC[scheduler-service]
  SVC -->|produce| K1[[Kafka: scheduler.commands.v1]]

  K1 -->|consume (writer group)| WR[KafkaCommandWriterLoop]
  WR -->|insertIfAbsent| DB1[(Postgres: t_command)]

  K1 -->|consume (master group)| MC[KafkaMasterCommandConsumerLoop]
  MC -->|dedupe| DB2[(Postgres: t_command_dedupe)]
  MC -->|materialize| DB3[(Postgres: t_workflow_instance / t_workflow_plan / t_trigger)]
```
