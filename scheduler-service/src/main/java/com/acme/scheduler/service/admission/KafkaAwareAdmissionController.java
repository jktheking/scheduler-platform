package com.acme.scheduler.service.admission;

import com.acme.scheduler.service.port.AdmissionController;
import com.acme.scheduler.service.port.KafkaPressureSampler;

import java.util.Objects;

/**
 * Admission controller that composes:
 * - a base admission (rate limiting)
 * - local in-flight limiter (server backpressure)
 * - Kafka pressure sampler (dependency-aware shedding)
 * - circuit breaker gate
 */
public final class KafkaAwareAdmissionController implements AdmissionController {

  private final AdmissionController base;
  private final InflightLimiter inFlight;
  private final KafkaPressureSampler sampler;
  private final SimpleCircuitBreaker breaker;

  private final double rejectAbovePressure; // e.g. 0.8

  public KafkaAwareAdmissionController(
      AdmissionController base,
      InflightLimiter inFlight,
      KafkaPressureSampler sampler,
      SimpleCircuitBreaker breaker,
      double rejectAbovePressure
  ) {
    this.base = Objects.requireNonNull(base);
    this.inFlight = Objects.requireNonNull(inFlight);
    this.sampler = Objects.requireNonNull(sampler);
    this.breaker = Objects.requireNonNull(breaker);
    this.rejectAbovePressure = rejectAbovePressure;
  }

  @Override
  public Decision decide(String tenantId, String operation) {
    // 1) rate limiting
    Decision d = base.decide(tenantId, operation);
    if (d.type() != DecisionType.ALLOW) return d;

    // 2) circuit breaker
    if (!breaker.allowRequest()) {
      return Decision.rejectBackpressure("circuit breaker open");
    }

    // 3) local in-flight
    if (!inFlight.tryAcquire()) {
      return Decision.rejectBackpressure("inflight limit reached");
    }

    // 4) dependency-aware shedding
    KafkaPressureSampler.PressureSample ps = sampler.sample();
    if (ps.pressure() >= rejectAbovePressure) {
      inFlight.release();
      return Decision.rejectBackpressure("kafka pressure=" + ps.pressure() + " reason=" + ps.reason());
    }

    // accepted; caller should release() when done
    return Decision.allow();
  }

  /**
   * Must be called by the ingestion pipeline once it finishes doing the work
   * (e.g., after publish completes / request is done).
   */
  public void release() {
    inFlight.release();
  }

  public void onKafkaPublishSuccess() {
    breaker.onSuccess();
  }

  public void onKafkaPublishFailure() {
    breaker.onFailure();
  }
}
