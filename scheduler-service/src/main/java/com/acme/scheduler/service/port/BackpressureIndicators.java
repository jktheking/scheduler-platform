package com.acme.scheduler.service.port;


public interface BackpressureIndicators {

  /**
   * If true, API should reject new requests (or shed load) because downstream is saturated.
   */
  boolean isOverloaded();

  /**
   * Human-readable reason for overload (for logs / metrics).
   */
  String reason();
}

