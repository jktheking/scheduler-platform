# Kafka Topics + Consumer Roles (Writer vs Master)

```mermaid
flowchart LR
  subgraph Kafka
    C[[scheduler.commands.v1]]
    R[[scheduler.tasks.ready.v1]]
    S[[scheduler.task.state.v1]]
    A[[scheduler.alerts.v1]]
  end

  subgraph Consumers
    WR[KafkaCommandWriterLoop\n(consumer group: scheduler-command-writer)]
    MC[KafkaMasterCommandConsumerLoop\n(consumer group: scheduler-master)]
    WK[scheduler-worker\n(consumes READY)]
    TS[KafkaTaskStateConsumerLoop\n(master consumes state)]
    ALS[scheduler-alert-server\n(consumes alerts)]
  end

  C --> WR
  C --> MC
  MC --> R
  R --> WK
  WK --> S
  S --> TS
  TS --> A
  A --> ALS
```
