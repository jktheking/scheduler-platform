package com.acme.scheduler.master.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link com.acme.scheduler.master.runtime.TaskTimeoutReconciler}.
 *
 * <p>Boundaries:
 * <ul>
 *   <li>This is adapter/wiring configuration (Spring) and must not be referenced from domain/core.</li>
 *   <li>Defaults are chosen to be safe for demos and small deployments.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "scheduler.master.timeout-reconciler")
public class TaskTimeoutReconcilerProperties {

  /** How often to scan for timed-out RUNNING tasks. */
  private long pollMs = 5_000;

  /** Max tasks to evaluate per scan. */
  private int batch = 200;

  public long getPollMs() {
    return pollMs;
  }

  public void setPollMs(long pollMs) {
    this.pollMs = pollMs;
  }

  public int getBatch() {
    return batch;
  }

  public void setBatch(int batch) {
    this.batch = batch;
  }
}
