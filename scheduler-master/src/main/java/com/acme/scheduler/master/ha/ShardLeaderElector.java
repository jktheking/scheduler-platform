package com.acme.scheduler.master.ha;

import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Per-shard leader election.
 *
 * <p>Keyspace: /scheduler/master/shards/&lt;shardId&gt;/leader (configurable).
 */
public interface ShardLeaderElector {

  /** Start background campaigning/keepalive. */
  void start();

  /** Stop and resign all shard leadership. */
  void stop();

  /** True if this node currently leads the shard. */
  boolean isLeader(int shardId);

  /** Shards currently led by this node. */
  Set<Integer> leaderShards();

  /** Optional monotonic-ish epoch/term per shard (for debugging). */
  OptionalLong leaderEpoch(int shardId);

  /** Listener invoked on leadership changes (best-effort). */
  void addListener(Consumer<Integer> onAnyShardChange);
}
