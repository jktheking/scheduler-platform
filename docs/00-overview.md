# Overview

This repository is a production-grade distributed job scheduler inspired by Apache Scheduler, but redesigned for:

- **Clean Architecture**: core scheduling logic is framework-agnostic
- **Extreme scale**:
 - **1M schedule create requests/sec** via ingestion pipeline + batching + idempotency
 - **10M jobs due at the same instant** via sharded time wheels + bucket-pointer fanout
- **Pluggability** via SPI plugins: tasks, datasources, storage, registry, alerts, and scale strategies
- **Full observability** via OpenTelemetry (traces, metrics, logs), with Prometheus + Grafana integration
- **Cloud-native deployment** via Docker Compose + Helm (microk8s)

## Runtime servers (independent processes)

- **scheduler-api**: HTTP control plane for UI and external clients
- **scheduler-master**: scheduling + orchestration (DAG analysis, dispatch)
- **scheduler-worker**: task execution engine
- **scheduler-alert-server**: notification service

## Non-goals for Step 0.x

Step 0.x only establishes the repository foundation and initial docs.
Domain model, APIs, DB schema, scheduling engines, and plugins arrive in Step 1+.
