package com.acme.scheduler.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Worker-side shard hint used only for Kafka partition alignment.
 *
 * <p>The worker itself is stateless with respect to shard leadership, but it publishes
 * task state events that the master processes shard-scoped. For production you should
 * set {@code shards} to the same value as {@code scheduler.master.sharding.shards} and
 * set topic partitions to that value.
 */
@ConfigurationProperties(prefix = "scheduler.worker.sharding")
public class WorkerShardingProperties {

  /** Enable shard-aligned partitioning for task state publishes. Default: false (demo safe). */
  private boolean enabled = false;

  /** Intended shard count. Default: 1. */
  private int shards = 1;

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }

  public int getShards() { return shards; }
  public void setShards(int shards) { this.shards = shards; }
}
