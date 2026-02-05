

## `07-state-trigger-lifecycle.md`

# State Diagram — Trigger Lifecycle (DB only for triggers)

DB holds triggers only. Ready queue is Kafka.

```mermaid
stateDiagram-v2
  [*] --> DUE
  DUE --> ENQUEUED: claimed with skip locked
  ENQUEUED --> PROCESSING: publish ready started
  PROCESSING --> DONE: publish ack and mark done
  PROCESSING --> FAILED: publish failed or exception
  FAILED --> DUE: retry policy future enhancement
  DONE --> [*]
