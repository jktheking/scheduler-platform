package com.acme.scheduler.adapter.inmemory;

import java.util.concurrent.Semaphore;

import com.acme.scheduler.service.port.AdmissionController;

/**
 * Simple in-memory admission controller with a fixed permit count.
 * Use as a local backpressure mechanism in dev/test.
 */
public final class InMemoryAdmissionController implements AdmissionController {

  private final Semaphore permits;

  public InMemoryAdmissionController(int maxInFlight) {
    this.permits = new Semaphore(maxInFlight);
  }

  @Override
  public Decision decide(String tenantId, String operation) {
    if (permits.tryAcquire()) return Decision.allow();
    return Decision.rejectBackpressure("Backpressure: admission rejected");
  }

  public void release() {
    permits.release();
  }
}
