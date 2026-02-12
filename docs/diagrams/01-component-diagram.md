# Component Diagram — Scheduler Platform (Demo Topology)

Flowchart that matches the current project modules and Kafka topics.

```mermaid
flowchart LR
  U[Client / UI]

  subgraph App[Scheduler Platform]
    API[scheduler-api]
    SVC[scheduler-service]
    MASTER[scheduler-master]
    WORKER[scheduler-worker]
    ALERT[scheduler-alert-server]

    MW[KafkaCommandWriterLoop]
    MC[KafkaMasterCommandConsumerLoop]
    TE[TimeWheelTriggerEngine]
    DR[DefaultDagRuntime]
    TS[KafkaTaskStateConsumerLoop]
  end

  subgraph Topics[Kafka Topics]
    T1[[scheduler.commands.v1]]
    T2[[scheduler.tasks.ready.v1]]
    T3[[scheduler.task.state.v1]]
    T4[[scheduler.alerts.v1]]
  end

  subgraph Infra[Infra]
    P[(Postgres)]
    K[(Kafka)]
    O[OTel Collector]
    PR[(Prometheus)]
    G[Grafana]
  end

  %% Client -> API
  U -->|HTTP| API
  API -->|calls| SVC

  %% Service -> Commands WAL
  SVC -->|publish CommandEnvelope| T1

  %% Commands WAL consumers
  T1 -->|consume writer group| MW
  T1 -->|consume leader gated| MC

  MW -->|persist raw WAL| P
  MC -->|materialize instance plan trigger| P

  %% Trigger + DAG runtime inside master
  TE -->|claim triggers| P
  TE -->|wake| DR

  %% Master -> Ready tasks
  DR -->|select queued tasks| P
  DR -->|publish TaskDispatchEnvelope| T2

  %% Worker execution + state
  T2 -->|consume| WORKER
  WORKER -->|claim and update task| P
  WORKER -->|publish TaskStateEvent| T3

  %% Master consumes task state -> progress/retry/DLQ + alerts
  T3 -->|consume leader gated| TS
  TS -->|progress retry dlq| P
  TS -->|publish AlertEvent| T4

  %% Alert server
  T4 -->|consume| ALERT

  %% Telemetry
  API --> O
  MASTER --> O
  WORKER --> O
  ALERT --> O

  PR -->|scrape| API
  PR -->|scrape| MASTER
  PR -->|scrape| WORKER
  PR -->|scrape| ALERT

  G --> PR
