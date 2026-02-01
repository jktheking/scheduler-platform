# Architecture

This document describes the target architecture and the core data/control flows.

## Component diagram

```mermaid
flowchart TB
  subgraph client_layer[client_layer]
    UI[scheduler-ui<br/>React + MUI]
  end

  subgraph api_layer[API_Layer]
    API[scheduler-api<br/>ApiApplicationServer<br/>REST + Validation + Idempotency]
  end

  subgraph service_layer[Service_Layer]
    SVC[scheduler-service<br/>ProcessService + ExecutorService]
    DAO[scheduler-dao<br/>Repositories + Migrations]
  end

  subgraph control_plane[Control plane]
    MST[scheduler-master<br/>MasterServer<br/>MasterSchedulerThread<br/>MasterExecThread]
    QZ[Quartz Engine]
    TW[TimeWheel Engine<br/>sharded]
  end

  subgraph execution_plane[Execution plane]
    WRK[scheduler-worker<br/>WorkerServer<br/>TaskExecuteThread]
  end

  subgraph notification_plane[Notification plane]
    ALT[scheduler-alert-server<br/>AlertServer]
  end

  subgraph infrastructure[Infrastructure Layer]
    REG[scheduler-registry-all<br/>etcd / jdbc / zk]
    STG[scheduler-storage-all<br/>local / s3 / hdfs]
    META[metadata-storage-all<br/>rdbms / cassandra / clickhouse]
    BUS[(Command / Trigger / Dispatch Bus<br/>Kafka or NATS JetStream)]
  end

  UI --> API
  API --> SVC --> DAO --> META
  MST --> SVC
  MST --> QZ
  MST --> TW
  MST --> BUS
  BUS --> WRK
  WRK --> META
  WRK --> STG
  MST <--> REG
  WRK <--> REG
  ALT <--> REG
  MST --> ALT
```

## Flow A — Schedule creation at 1M requests/sec (ingestion pipeline)

Key idea: **Do not synchronously write every create request to the metadata DB**.
Instead, treat the API as an ingestion gateway and persist via **batched writers**.

```mermaid
sequenceDiagram
 autonumber
 participant UI as scheduler-ui
 participant API as scheduler-api
 participant BUS as CommandIngestBus (Kafka/JetStream)
 participant WR as ScheduleWriter (consumer fleet)
 participant DB as MetadataStore (RDBMS/other)

 UI->>API: POST/schedules (idempotencyKey, tenant, payload)
 API->>API: validate + assign scheduleId
 API->>BUS: publish ScheduleCreateRequested(scheduleId,...)
 API-->>UI: 202 Accepted (scheduleId, status=QUEUED)

 loop batched persistence
 WR->>BUS: pull batch (partitioned)
 WR->>DB: INSERT schedules (batch) + idempotency record
 WR-->>BUS: ack batch
 end
```

## Flow B — 10M jobs due at the same instant (bucket-pointer fanout)

Key idea: **Do not emit 10M trigger messages.**
Instead, timer shards emit **N pointer messages** (e.g., 4096) and dispatchers drain jobs in pages.

```mermaid
sequenceDiagram
 autonumber
 participant TW as TimeWheel Shards
 participant BUS as TriggerFanoutBus
 participant DSP as Dispatcher Fleet
 participant DB as MetadataStore
 participant Q as TaskDispatchQueue
 participant WK as Workers

 Note over TW: schedules are sharded by hash(scheduleId) -> shard
 TW->>BUS: publish DueBucketPointer(time=T, shard=i, pageRef=...)
 TW->>BUS: publish DueBucketPointer(time=T, shard=i+1, pageRef=...)
 Note over BUS: only O(shards) messages, not O(jobs)

 loop drain pages
 DSP->>BUS: consume DueBucketPointer
 DSP->>DB: fetch due scheduleIds page (e.g., 10k)
 DSP->>DB: bulk create execution attempts (idempotent)
 DSP->>Q: enqueue task references (batched)
 end

 WK->>Q: poll/claim tasks
 WK->>DB: update task state (RUNNING/SUCCESS/FAILURE)
```

## Architectural invariants (non-negotiable)

- **DB is the source of truth**, but DB is not used as the high-rate queue for extreme scale.
- **At-least-once dispatch** with **idempotent attempt creation**.
- **Backpressure** is applied at ingestion, fanout, and dispatch layers.
- **Observability-first**: metrics/traces/logs must allow answering:
 - How late are triggers?
 - Where is queueing happening?
 - Which tenant is saturating capacity?
