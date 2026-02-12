package com.acme.scheduler.service.admission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCircuitBreakerTest {

  @Test
  void opensAfterFailuresAndClosesAfterSuccess() {
    SimpleCircuitBreaker cb = new SimpleCircuitBreaker(3, java.time.Duration.ofSeconds(60), 1);
    assertTrue(cb.allowRequest());

    cb.onFailure();
    cb.onFailure();
    assertTrue(cb.allowRequest());

    cb.onFailure();
    assertFalse(cb.allowRequest());

    cb.onSuccess();
    assertTrue(cb.allowRequest());
  }
}
