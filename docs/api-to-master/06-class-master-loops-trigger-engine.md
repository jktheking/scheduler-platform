# Class Diagram — Master Runtime (Consumers, Trigger Engine, DAG Runtime)

This diagram shows the **scheduler-master** runtime pieces that execute the orchestration path.

Notes:
- Commands are consumed from Kafka WAL (`scheduler.commands.v1`).
- Triggers are stored in Postgres (`t_trigger`) and claimed with `SKIP LOCKED`.
- Ready tasks are published to Kafka (`scheduler.tasks.ready.v1`).
- Task state events are consumed from Kafka (`scheduler.task.state.v1`) to progress the DAG.

```mermaid
classDiagram
  direction TB

  class KafkaMasterCommandConsumerLoop {
    +run() void
  }

  class KafkaCommandWriterLoop {
    +run() void
  }

  class TimeWheelTriggerEngine {
    +start() void
    +stop() void
  }

  class DefaultDagRuntime {
    +onTriggerDue(trigger) void
    +onWorkflowSlaDue(trigger) void
    +onTaskState(event) void
  }

  class KafkaTaskStateConsumerLoop {
    +run() void
  }

  class JdbcCommandDedupeRepository {
    +tryMarkProcessing(commandId) boolean
    +markOutcome(commandId, outcome, detail) void
  }

  class JdbcWorkflowScheduler {
    +createAndSchedule(command) void
  }

  class JdbcTriggerRepository {
    +loadUpcoming(...) triggers
    +claimDue(...) triggers
    +markEnqueued(triggerId) void
    +markTerminal(triggerId, status, lastError) void
    +scheduleWorkflowSlaTrigger(wiId, dueTime) void
  }

  class JdbcTaskInstanceRepository {
    +queueRoots(...) int
    +moveRetryWaitToQueued(...) int
  }

  class KafkaReadyPublisher {
    +publish(dispatchEnvelope) void
  }

  KafkaMasterCommandConsumerLoop --> JdbcCommandDedupeRepository
  KafkaMasterCommandConsumerLoop --> JdbcWorkflowScheduler
  JdbcWorkflowScheduler --> JdbcTriggerRepository

  TimeWheelTriggerEngine --> JdbcTriggerRepository
  TimeWheelTriggerEngine --> DefaultDagRuntime

  DefaultDagRuntime --> JdbcTaskInstanceRepository
  DefaultDagRuntime --> KafkaReadyPublisher

  KafkaTaskStateConsumerLoop --> DefaultDagRuntime
```
