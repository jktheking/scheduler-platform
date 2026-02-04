# Scheduler Platform – Demo & Local Development Guide

This repository contains a **production-grade distributed scheduler platform** with:
- Kafka-based ingestion
- PostgreSQL persistence
- Clean Architecture (API / Master / Worker separation)
- OpenTelemetry-based metrics
- Prometheus + Grafana observability
- Fully Dockerized local demo

This README explains **exactly** how to build, run, inspect, debug, and validate the system end-to-end.

---

## A. Repository Structure


---

## B. Prerequisites

### Required
- Docker Desktop (with Compose v2)
- JDK **25** (only if building locally)
- 8GB RAM recommended

### Optional (for debugging)
- `curl`
- `jq`

---

## C. Build the Application Image

> Run from **repository root**

```bash
docker build \
  --build-arg APP_MODULE=scheduler-api \
  --build-arg JAVA_VERSION=25 \
  -t scheduler-api:demo .

  
#Clean rebuild (recommended if things behave oddly)  
docker compose -f demo/docker-compose.app.yml down
docker rmi scheduler-api:demo
docker builder prune -f


# Start Infra (Kafka, DB, Observability)

Run from repo root

docker compose -f demo/docker-compose.infra.yml up -d

This creates:

Kafka (KRaft)
PostgreSQL + Flyway
Kafka UI
OpenTelemetry Collector
Prometheus
Grafana

Shared Docker network: scheduler-net


docker compose -f demo/docker-compose.app.yml up


| Service       | URL                                                                            | Notes                                                     |
| ------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------- |
| Scheduler API | [http://localhost:8080](http://localhost:8080)                                 | REST server                                               |
| Swagger UI    | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | API testing                                               |
| Kafka UI      | [http://localhost:8088](http://localhost:8088)                                 | Topic inspection                                          |
| Prometheus    | [http://localhost:9090](http://localhost:9090)                                 | Metrics store                                             |
| Grafana       | [http://localhost:3000](http://localhost:3000)                                 | admin / admin                                             |
| PostgreSQL    | localhost:5432                                                                 | JDBC                                                      |
| pgAdmin       | [http://localhost:5050](http://localhost:5050)                                 | [admin@scheduler.com](mailto:admin@scheduler.com) / admin |



#Kafka Topics & Scheduler Flow
| Topic                      | Purpose            |
| -------------------------- | ------------------ |
| `scheduler.commands.v1`    | Ingestion commands |
| `scheduler.tasks.ready.v1` | Ready-to-run tasks |
| `scheduler.tasks.retry.v1` | Retry queue        |



#Testing the Scheduler API (Swagger)
http://localhost:8080/swagger-ui.html


Start Workflow
{
  "workflowId": "demo-workflow-1",
  "scheduleAt": "2026-02-03T18:00:00Z",
  "dag": {
    "nodes": ["A", "B"],
    "edges": [["A", "B"]]
  }
}



#K. PostgreSQL & Flyway
Flyway

Runs automatically on infra startup

Migration path:
 - scheduler-dao/src/main/resources/db/migration

Verify Schema
Open pgAdmin → Connect to scheduler
Inspect workflow, task_instance, command_log

  