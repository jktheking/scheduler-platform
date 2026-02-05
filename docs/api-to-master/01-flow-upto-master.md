# Scheduler Platform — Implemented Flow upto master

This document describes the **actual implemented flow** up to **scheduler-master**:

- `scheduler-api → scheduler-service` (command ingestion)
- **Two ingestion modes**:
  1) **Low TPS**: Direct JDBC write (Command persisted)
  2) **High TPS**: Kafka WAL write (`scheduler.commands.v1`)
- **Two Kafka consumer groups** on the same `scheduler.commands.v1` topic:
  - `scheduler-command-writer` persists command into Postgres (`t_command`)
  - `scheduler-master` consumes for orchestration (dedupe → instance/plan → triggers)
- **Trigger engine** produces ready events to Kafka (`scheduler.tasks.ready.v1`)
  - Engine is switchable: **TimeWheel** or **Quartz**
- **No DB ready queue**. DB holds *triggers* only; ready queue is Kafka.

---

## 1) Component Diagram (UML-style)

```mermaid
flowchart TB
  %% Client
  subgraph Client
    UI[Client UI]
  end

  %% API + Service
  subgraph API
    CTRL[WorkflowExecutionController]
  end

  subgraph SVC
    UC[StartWorkflowUseCase]
    ADM[Admission Stack<br/>TokenBucket<br/>InflightLimiter<br/>KafkaPressureSampler<br/>CircuitBreaker]
    PORT[CommandIngestionGateway<br/>port]
  end

  %% Adapters
  subgraph JDBC
    JGW[JdbcCommandIngestionGateway]
    JREPO[JdbcCommandRepository]
  end

  subgraph KAFKA_ADP
    KGW[KafkaWalCommandIngestionGateway]
    KBUS[KafkaCommandBus Producer]
    KPS[KafkaPressureSamplerImpl]
  end

  %% Kafka
  subgraph KAFKA
    TOPIC1[(scheduler.commands.v1)]
    TOPIC2[(scheduler.tasks.ready.v1)]
  end

  %% Master
  subgraph MASTER
    CGW[KafkaCommandWriterLoop<br/>consumer-group scheduler-command-writer]
    CMC[KafkaMasterCommandConsumerLoop<br/>consumer-group scheduler-master]
    PROC[MasterCommandProcessor<br/>dedupe and schedule]
    DEDUPE[(t_command_dedupe)]
    TRIGENG[TriggerEngine<br/>TimeWheel or Quartz]
    READY[KafkaReadyPublisher]
  end

  %% DB
  subgraph PG
    TCOMMAND[(t_command)]
    TWI[(t_workflow_instance)]
    TPLAN[(t_workflow_plan)]
    TTRIG[(t_trigger)]
  end

  %% Edges
  UI --> CTRL
  CTRL --> UC
  UC --> ADM
  ADM --> PORT

  PORT -->|Low TPS| JGW
  JGW --> JREPO
  JREPO --> TCOMMAND

  PORT -->|High TPS WAL| KGW
  KGW --> KBUS
  KBUS --> TOPIC1
  KPS --> ADM

  TOPIC1 --> CGW
  CGW --> TCOMMAND

  TOPIC1 --> CMC
  CMC --> PROC
  PROC --> DEDUPE
  PROC --> TWI
  PROC --> TPLAN
  PROC --> TTRIG

  TRIGENG --> TTRIG
  TRIGENG --> READY
  READY --> TOPIC2