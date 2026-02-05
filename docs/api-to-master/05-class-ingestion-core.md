# Class Diagram — Ingestion Core (Command + Ports + Admission)

This diagram covers the **core ingestion types** in `scheduler-service` and the
ports/adapters for JDBC and Kafka WAL ingestion.

```mermaid
classDiagram
  direction LR

  class StartWorkflowUseCase {
    +execute(req) StartWorkflowResult
  }

  class CommandEnvelope {
    +String commandId
    +String tenantId
    +String idempotencyKey
    +String commandType
    +long workflowCode
    +int workflowVersion
    +Instant createdAt
    +String payloadJson
  }

  class AdmissionController {
    <<interface>>
    +admit(env) Decision
  }

  class Decision {
    +String type
    +String reason
  }

  class TokenBucketAdmissionController {
    +admit(env) Decision
  }

  class InflightLimiter {
    +tryAcquire() boolean
    +release() void
  }

  class KafkaPressureSampler {
    <<interface>>
    +pressure() double
  }

  class KafkaPressureSamplerImpl {
    +pressure() double
  }

  class KafkaAwareAdmissionController {
    +admit(env) Decision
  }

  class SimpleCircuitBreaker {
    +admit(env) Decision
  }

  class CommandIngestionGateway {
    <<interface>>
    +ingest(env) IngestResult
  }

  class IngestResult {
    +boolean accepted
    +String commandId
    +String reason
  }

  class JdbcCommandIngestionGateway {
    +ingest(env) IngestResult
  }

  class KafkaWalCommandIngestionGateway {
    +ingest(env) IngestResult
  }

  StartWorkflowUseCase --> CommandEnvelope
  StartWorkflowUseCase --> AdmissionController
  StartWorkflowUseCase --> CommandIngestionGateway

  AdmissionController <|.. TokenBucketAdmissionController
  AdmissionController <|.. KafkaAwareAdmissionController
  AdmissionController <|.. SimpleCircuitBreaker

  KafkaAwareAdmissionController --> KafkaPressureSampler
  KafkaPressureSampler <|.. KafkaPressureSamplerImpl
  KafkaAwareAdmissionController --> InflightLimiter

  CommandIngestionGateway <|.. JdbcCommandIngestionGateway
  CommandIngestionGateway <|.. KafkaWalCommandIngestionGateway
