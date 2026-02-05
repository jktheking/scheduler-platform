# Master Role Split — Sequence Diagram

This sequence diagram shows how the same Kafka topic
(`scheduler.commands.v1`) is consumed by **two different consumer groups**
running the same `scheduler-master` artifact in different roles:

- **WRITER** — persists commands into Postgres
- **MASTER** — performs orchestration and publishes ready tasks

---

```mermaid
sequenceDiagram
  autonumber

  participant API as SchedulerAPI
  participant K as KafkaCommandsTopic
  participant WR as MasterWriter
  participant MS as MasterConsumer
  participant DB as Postgres
  participant KR as KafkaReadyTopic

  API->>K: produce scheduler.commands.v1

  par writer persistence
    WR->>K: poll commands
    K-->>WR: CommandEnvelope
    WR->>DB: insert into t_command
    DB-->>WR: ok or duplicate
  and master orchestration
    MS->>K: poll commands
    K-->>MS: CommandEnvelope
    MS->>DB: dedupe instance plan trigger
    MS->>KR: publish ready when due
  end
