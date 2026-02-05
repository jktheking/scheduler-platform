# Kafka Topics, Consumer Groups, and Master Role Split

This document describes:

1. Kafka topics and their associated consumer groups
2. How the `scheduler-master` process is split into **WRITER** and **MASTER** roles
3. Why this split is required for scalability and stability

---

## 1) Kafka Topics and Consumer Groups

The scheduler platform uses **two Kafka topics**:

- `scheduler.commands.v1`  
  Carries command WAL events produced by `scheduler-api`

- `scheduler.tasks.ready.v1`  
  Carries ready-to-execute task events produced by the trigger engine

There are **two independent consumer groups** on `scheduler.commands.v1`.

```mermaid
flowchart LR
  subgraph Kafka
    C[(scheduler.commands.v1)]
    R[(scheduler.tasks.ready.v1)]
  end

  subgraph ConsumerGroups
    WR[consumer-group scheduler-command-writer<br/>role writer]
    MS[consumer-group scheduler-master<br/>role master]
  end

  C --> WR
  C --> MS
  MS --> R
  
  
  
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
