## `08-notes-guarantees.md`

# Notes / Guarantees (as implemented)

## Delivery semantics

- Kafka command delivery is **at least once**.
- Master-side **dedupe** prevents reprocessing the same command more than once.

## Dedupe

- Table: `t_command_dedupe`
- Primary key: `command_id`

## Command persistence

Writer consumer group uses idempotent insert into `t_command`:

- `t_command` has a unique constraint on:
  - `(tenant_id, idempotency_key)`

This guarantees:
- Replays do not create duplicate commands
- API retries do not create duplicate commands

## Ready queue

- Ready queue is **Kafka only**:
  - topic: `scheduler.tasks.ready.v1`
- DB ready queue is **not used**.

## Trigger engine

Trigger engines are swappable:
- `TimeWheel` for burst handling
- `Quartz` for predictable polling
