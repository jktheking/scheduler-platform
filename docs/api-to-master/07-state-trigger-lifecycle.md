# State Diagram — Trigger Lifecycle (`t_trigger.status`)

Triggers are **DB-owned** scheduling primitives. The master claims them with
`FOR UPDATE SKIP LOCKED` and executes the corresponding wakeup.

Key behaviors:
- Triggers are inserted as `DUE`.
- Loader may mark upcoming triggers `ENQUEUED` when placed into the in-memory wheel.
- Claimer marks triggers `PROCESSING`.
- Trigger is terminal as `DONE` or `FAILED` (with `last_error`).

```mermaid
stateDiagram-v2
  [*] --> DUE

  DUE --> ENQUEUED: loader places in wheel (optional)
  DUE --> PROCESSING: claimed directly (SKIP LOCKED)

  ENQUEUED --> PROCESSING: claimed (SKIP LOCKED)

  PROCESSING --> DONE: handler ok
  PROCESSING --> FAILED: handler exception

  DONE --> [*]
  FAILED --> [*]
```
