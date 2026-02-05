## `04-seq-master-consume-trigger-ready.md`


#### Sequence Diagram — Master Consumption + Trigger Scheduling + Ready Publish

This sequence diagram shows how `scheduler-master` (master consumer group) consumes
commands, performs **dedupe**, materializes **instance/plan/trigger**, and how the
**trigger engine** publishes ready events to Kafka (`scheduler.tasks.ready.v1`).

```mermaid
sequenceDiagram
  autonumber

  participant K as KafkaCommandsTopic
  participant MC as MasterConsumer
  participant DP as DedupeRepo
  participant WI as WorkflowInstanceRepo
  participant PL as WorkflowPlanRepo
  participant TR as TriggerRepo
  participant ENG as TriggerEngine
  participant RP as ReadyPublisher
  participant KT as KafkaReadyTopic

  MC->>K: poll commands
  K-->>MC: CommandEnvelope

  MC->>DP: markProcessedIfAbsent commandId

  alt duplicate command
    DP-->>MC: false
    MC-->>K: commit offset
  else first time
    DP-->>MC: true
    MC->>WI: createWorkflowInstance
    WI-->>MC: workflowInstanceId
    MC->>PL: persistPlan workflowInstanceId
    PL-->>MC: ok
    MC->>TR: insertTrigger dueTime status DUE
    TR-->>MC: ok
    MC-->>K: commit offset
  end

  loop trigger engine tick or poll
    ENG->>TR: claimDueTriggers for update skip locked
    TR-->>ENG: triggers
    ENG->>RP: publish TaskReadyEvent
    RP->>KT: produce scheduler.tasks.ready.v1
    KT-->>RP: ack
    ENG->>TR: mark trigger DONE
  end
