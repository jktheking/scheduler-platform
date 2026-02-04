package com.acme.scheduler.common.concurrent;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free-ish token bucket for rate limiting.
 *
 * - capacity: max tokens
 * - refillTokensPerSecond: steady refill rate
 *
 * This is deliberately dependency-free to keep core logic portable.
 */
public final class TokenBucket {

  private final long refillTokensPerSecond;
  private final long capacity;

  private final AtomicLong available;
  private final AtomicLong lastRefillNanos;

  public TokenBucket(long refillTokensPerSecond, long capacity) {
    if (refillTokensPerSecond <= 0) throw new IllegalArgumentException("refillTokensPerSecond must be > 0");
    if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
    this.refillTokensPerSecond = refillTokensPerSecond;
    this.capacity = capacity;
    this.available = new AtomicLong(capacity);
    this.lastRefillNanos = new AtomicLong(System.nanoTime());
  }

  public boolean tryConsume(long tokens) {
    if (tokens <= 0) return true;
    refillIfNeeded();

    while (true) {
      long cur = available.get();
      if (cur < tokens) return false;
      long next = cur - tokens;
      if (available.compareAndSet(cur, next)) return true;
    }
  }

  public long availableTokens() {
    refillIfNeeded();
    return available.get();
  }

  private void refillIfNeeded() {
    long now = System.nanoTime();
    long prev = lastRefillNanos.get();
    long elapsed = now - prev;
    if (elapsed <= 0) return;

    long tokensToAdd = (elapsed * refillTokensPerSecond) / 1_000_000_000L;
    if (tokensToAdd <= 0) return;

    if (lastRefillNanos.compareAndSet(prev, now)) {
      available.getAndUpdate(v -> {
        long next = v + tokensToAdd;
        return next > capacity ? capacity : next;
      });
    }
  }
}
