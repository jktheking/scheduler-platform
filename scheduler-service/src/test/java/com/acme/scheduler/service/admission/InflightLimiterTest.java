package com.acme.scheduler.service.admission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InflightLimiterTest {

  @Test
  void acquireAndRelease_enforcesLimit() {
    InflightLimiter lim = new InflightLimiter(2);
    assertTrue(lim.tryAcquire());
    assertTrue(lim.tryAcquire());
    assertFalse(lim.tryAcquire());

    lim.release();
    assertTrue(lim.tryAcquire());
  }
}
