package com.acme.scheduler.master.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Repair loop settings for Kafka-only ready queue.
 */
@ConfigurationProperties(prefix = "scheduler.master.enqueue-reconciler")
public class TaskEnqueueReconcilerProperties {

  /**
   * How often to scan for QUEUED tasks that may have missed a ready publish.
   */
  private long intervalMs = 2_000;

  /**
   * Consider a QUEUED task "stale" and eligible for re-enqueue after this delay.
   * Keep this small to avoid permanent stuck tasks when Kafka send callbacks fail.
   */
  private long reenqueueAfterMs = 2_000;

  private int batch = 200;

  public long getIntervalMs() { return intervalMs; }
  public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }

  public long getReenqueueAfterMs() { return reenqueueAfterMs; }
  public void setReenqueueAfterMs(long reenqueueAfterMs) { this.reenqueueAfterMs = reenqueueAfterMs; }

  public int getBatch() { return batch; }
  public void setBatch(int batch) { this.batch = batch; }
}
