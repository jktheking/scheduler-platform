package com.acme.scheduler.master.sharding;

/**
 * Deterministic shard mapping for master-owned orchestration.
 *
 * <p>Shard key: workflow_instance_id (and related keys that must map consistently).
 * Uses a stable 64-bit mix to distribute sequential ids.
 */
public final class ShardCalculator {
  private final int shards;

  public ShardCalculator(int shards) {
    if (shards <= 0) throw new IllegalArgumentException("shards must be > 0");
    this.shards = shards;
  }

  public int shards() { return shards; }

  public int shardForWorkflowInstanceId(long workflowInstanceId) {
    return shardForLong(workflowInstanceId);
  }

  /** Used for pre-instance routing (e.g., commands keyed by workflow_code in the current architecture). */
  public int shardForWorkflowCode(long workflowCode) {
    return shardForLong(workflowCode);
  }

  private int shardForLong(long v) {
    long h = mix64(v);
    // shards can be any positive; power-of-two works fine but not required.
    int mod = (int) Math.floorMod(h, shards);
    return mod;
  }

  // SplitMix64 mix function (fast, stable)
  private static long mix64(long z) {
    z += 0x9E3779B97F4A7C15L;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }
}
