package com.acme.scheduler.adapter.inmemory;

import java.util.concurrent.atomic.AtomicLong;

import com.acme.scheduler.service.port.AdmissionController;

/**
 * Lightweight token bucket admission controller for local dev and tests.
 *
 * Not distributed. For production (1M rps), use the production admission stack:
 * TokenBucketAdmissionController + InflightLimiter + KafkaAwareAdmissionController + circuit breaker.
 */
public final class SimpleTokenBucketAdmissionController implements AdmissionController {

  private final long refillTokensPerSecond;
  private final long capacity;

  private final AtomicLong available;
  private final AtomicLong lastRefillNanos = new AtomicLong(System.nanoTime());

  public SimpleTokenBucketAdmissionController(long refillTokensPerSecond, long capacity) {
    if (refillTokensPerSecond <= 0) throw new IllegalArgumentException("refillTokensPerSecond must be > 0");
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
    this.refillTokensPerSecond = refillTokensPerSecond;
    this.capacity = capacity;
    this.available = new AtomicLong(capacity);
  }

  @Override
  public Decision decide(String tenantId, String operation) {
    refillIfNeeded();
    long left = available.getAndUpdate(v -> v > 0 ? v - 1 : v);
    if (left <= 0) {
      return Decision.rejectRate("rate limit exceeded");
    }
    return Decision.allow();
  }

  private void refillIfNeeded() {
    long now = System.nanoTime();
    long prev = lastRefillNanos.get();
    long elapsedNanos = now - prev;
    if (elapsedNanos <= 0) return;

    long tokensToAdd = (elapsedNanos * refillTokensPerSecond) / 1_000_000_000L;
    if (tokensToAdd <= 0) return;

    if (lastRefillNanos.compareAndSet(prev, now)) {
      available.getAndUpdate(v -> {
        long next = v + tokensToAdd;
        return next > capacity ? capacity : next;
      });
    }
  }
}
