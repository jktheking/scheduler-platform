package com.acme.scheduler.service.admission;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal circuit breaker to protect hot paths from cascading failures.
 *
 * States:
 * - CLOSED: all allowed
 * - OPEN: reject fast until openDuration elapses
 * - HALF_OPEN: allow limited probes; if success -> CLOSED, if failure -> OPEN
 *
 * This is intentionally small and dependency-free. For production, you may
 * replace with resilience4j in infra layer, but keep this port-free core available.
 */
public final class SimpleCircuitBreaker {

  public enum State { CLOSED, OPEN, HALF_OPEN }

  private final int failureThreshold;
  private final Duration openDuration;
  private final int halfOpenMaxProbes;

  private final AtomicInteger failures = new AtomicInteger(0);
  private final AtomicLong openUntilNanos = new AtomicLong(0);
  private final AtomicInteger halfOpenProbes = new AtomicInteger(0);

  public SimpleCircuitBreaker(int failureThreshold, Duration openDuration, int halfOpenMaxProbes) {
    if (failureThreshold <= 0) throw new IllegalArgumentException("failureThreshold must be > 0");
    if (openDuration == null || openDuration.isNegative() || openDuration.isZero())
      throw new IllegalArgumentException("openDuration must be > 0");
    if (halfOpenMaxProbes <= 0) throw new IllegalArgumentException("halfOpenMaxProbes must be > 0");

    this.failureThreshold = failureThreshold;
    this.openDuration = openDuration;
    this.halfOpenMaxProbes = halfOpenMaxProbes;
  }

  public State state() {
    long until = openUntilNanos.get();
    long now = System.nanoTime();
    if (until > now) return State.OPEN;
    if (until != 0 && until <= now) return State.HALF_OPEN;
    return State.CLOSED;
  }

  /** Whether an operation is allowed right now. */
  public boolean allowRequest() {
    State s = state();
    if (s == State.CLOSED) return true;
    if (s == State.OPEN) return false;

    // HALF_OPEN
    int probes = halfOpenProbes.incrementAndGet();
    return probes <= halfOpenMaxProbes;
  }

  public void onSuccess() {
    failures.set(0);
    openUntilNanos.set(0);
    halfOpenProbes.set(0);
  }

  public void onFailure() {
    int f = failures.incrementAndGet();
    if (f >= failureThreshold) {
      openUntilNanos.set(System.nanoTime() + openDuration.toNanos());
      halfOpenProbes.set(0);
    }
  }
}
