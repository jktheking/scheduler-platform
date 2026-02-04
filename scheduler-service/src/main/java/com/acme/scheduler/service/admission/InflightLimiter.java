package com.acme.scheduler.service.admission;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Fast in-flight limiter to protect local resources (threads, heap, downstream capacity).
 *
 * This is NOT a rate limiter; it limits concurrent in-flight work.
 */
public final class InflightLimiter {

  private final Semaphore permits;

  public InflightLimiter(int maxInFlight) {
    if (maxInFlight <= 0) throw new IllegalArgumentException("maxInFlight must be > 0");
    this.permits = new Semaphore(maxInFlight);
  }

  public boolean tryAcquire() {
    return permits.tryAcquire();
  }

  public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
    return permits.tryAcquire(timeout, unit);
  }

  public void release() {
    permits.release();
  }

  public int availablePermits() {
    return permits.availablePermits();
  }
}
