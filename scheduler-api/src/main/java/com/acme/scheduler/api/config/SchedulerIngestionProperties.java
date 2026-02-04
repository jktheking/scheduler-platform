package com.acme.scheduler.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scheduler.ingestion")
public record SchedulerIngestionProperties(
    Mode mode,
    String tenantDefault,
    Admission admission,
    Jdbc jdbc,
    Kafka kafka
) {
  public enum Mode { INMEMORY, JDBC, KAFKA }

  public record Admission(
      long refillTokensPerSecond,
      long capacity,
      int maxTenants,
      int maxInFlight,
      double kafkaRejectAbovePressure,
      CircuitBreaker circuitBreaker
  ) {}

  public record CircuitBreaker(
      int failureThreshold,
      long openMillis,
      int halfOpenMaxProbes
  ) {}

  public record Jdbc(
      boolean enabled
  ) {}

  public record Kafka(
      boolean enabled,
      String bootstrapServers,
      String commandsTopic,
      String clientId
  ) {}

  public static SchedulerIngestionProperties defaults() {
    return new SchedulerIngestionProperties(
        Mode.INMEMORY,
        "default",
        new Admission(50_000, 100_000, 10_000, 20_000, 0.80,
            new CircuitBreaker(10, 10_000, 5)),
        new Jdbc(true),
        new Kafka(true, "localhost:9092", "scheduler.commands.v1", "scheduler-api")
    );
  }
}
