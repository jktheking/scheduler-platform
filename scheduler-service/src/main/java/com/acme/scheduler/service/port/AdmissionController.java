package com.acme.scheduler.service.port;

/**
 * Clean-architecture port for ingestion admission control.
 *
 * Implementations can layer:
 * - token bucket rate limiting (per tenant / per operation)
 * - in-flight limiting (server backpressure)
 * - dependency-aware shedding (e.g., Kafka pressure / DB slowness)
 */
public interface AdmissionController {

  enum DecisionType { ALLOW, REJECT_RATE_LIMIT, REJECT_BACKPRESSURE }

  record Decision(
      DecisionType type,
      String reason
  ) {
    public static Decision allow() { return new Decision(DecisionType.ALLOW, "ok"); }
    public static Decision rejectRate(String reason) { return new Decision(DecisionType.REJECT_RATE_LIMIT, reason); }
    public static Decision rejectBackpressure(String reason) { return new Decision(DecisionType.REJECT_BACKPRESSURE, reason); }
  }

  /**
   * Decide whether to accept an ingestion request.
   */
  Decision decide(String tenantId, String operation);

  /**
   * Convenience helper for call sites that prefer exceptions.
   */
  default void acquireOrThrow(String tenantId, String operation) {
    Decision d = decide(tenantId, operation);
    if (d.type() == DecisionType.ALLOW) return;
    throw new IllegalStateException(d.type() + ": " + d.reason());
  }
}
