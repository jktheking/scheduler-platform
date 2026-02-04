package com.acme.scheduler.adapter.inmemory;

/** Thrown by admission controllers when capacity is exceeded. */
public class RateLimitExceededException extends RuntimeException {
  public RateLimitExceededException(String message) {
    super(message);
  }
}
