## `06-class-master-loops-trigger-engine.md`


#### Class Diagram — Master (Command Loops + Trigger Engines)

This diagram covers `scheduler-master` runtime pieces:
- writer loop (WAL -> DB)
- master loop (orchestrate + triggers)
- trigger engine (TimeWheel or Quartz)
- ready publisher (Kafka)

```mermaid
classDiagram
  direction TB

  class KafkaCommandWriterLoop {
    +run() void
    -pollAndPersist() void
  }

  class KafkaMasterCommandConsumerLoop {
    +run() void
    -pollAndProcess() void
  }

  class MasterCommandProcessor {
    +process(env) void
  }

  class TriggerEngine {
    <<interface>>
    +start() void
    +stop() void
  }

  class TimeWheelTriggerEngine {
    +start() void
    +stop() void
  }

  class QuartzTriggerEngine {
    +start() void
    +stop() void
  }

  class KafkaReadyPublisher {
    +publish(evt) void
  }

  KafkaMasterCommandConsumerLoop --> MasterCommandProcessor
  MasterCommandProcessor --> TriggerEngine

  TriggerEngine <|.. TimeWheelTriggerEngine
  TriggerEngine <|.. QuartzTriggerEngine

  TimeWheelTriggerEngine --> KafkaReadyPublisher
  QuartzTriggerEngine --> KafkaReadyPublisher
