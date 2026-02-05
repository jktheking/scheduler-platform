## `09-deployment-topology-phase-b-c.md`

#### Deployment Topology — PHASE B + PHASE C (Local / Docker Compose)

This doc shows the PHASE B + C topology:

- `scheduler-api` (REST ingestion)
- `scheduler-master` in two roles:
  - WRITER (Kafka WAL -> DB)
  - MASTER (Kafka orchestration + TriggerEngine + Ready publish)
- Postgres
- Kafka
- Optional telemetry stack

## 1) Topology diagram

```mermaid
flowchart TB
  subgraph Net
    API[scheduler-api<br/>REST 8080]
    MW[scheduler-master writer<br/>consumer-group scheduler-command-writer]
    MM[scheduler-master master<br/>consumer-group scheduler-master]
    PG[(Postgres 5432)]
    K[(Kafka 9092)]
    OTEL[OTel Collector<br/>4317 4318]
    PROM[Prometheus<br/>9090]
    GRAF[Grafana<br/>3000]
  end

  API -->|JDBC mode| PG
  API -->|Kafka WAL mode| K

  K -->|consume commands| MW
  MW -->|persist t_command| PG

  K -->|consume commands| MM
  MM -->|dedupe instance plan triggers| PG

  MM -->|publish ready events| K

  API -.-> OTEL
  MW  -.-> OTEL
  MM  -.-> OTEL
  OTEL -.-> PROM
  PROM -.-> GRAF 