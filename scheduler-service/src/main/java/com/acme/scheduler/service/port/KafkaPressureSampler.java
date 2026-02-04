package com.acme.scheduler.service.port;

/**
 * Port for sampling Kafka-related pressure signals so ingestion can shed load before Kafka becomes a bottleneck.
 *
 * Implementations live in infrastructure (scheduler-adapter-kafka).
 */
public interface KafkaPressureSampler {

  record PressureSample(
      double pressure,   // 0.0 (healthy) .. 1.0 (max pressure)
      String reason
  ) {
    public static PressureSample healthy() { return new PressureSample(0.0, "ok"); }
  }

  PressureSample sample();
}
