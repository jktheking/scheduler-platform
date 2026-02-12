package com.acme.scheduler.master.ha;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * Non-HA (or local) leader elector.
 *
 * <p>Always reports leadership for all shards. This matches the single-master behavior
 * where the master owns the full responsibility set.
 */
public final class FixedShardLeaderElector implements ShardLeaderElector {

  private final Set<Integer> shards;
  private final CopyOnWriteArrayList<Consumer<Integer>> listeners = new CopyOnWriteArrayList<>();

  private volatile boolean started = false;

  public FixedShardLeaderElector(Set<Integer> shards) {
    this.shards = Set.copyOf(shards);
  }

  @Override
  public void start() {
    started = true;
    // Notify all listeners that we are the leader for all shards.
    for (int shard : shards) {
      for (Consumer<Integer> l : listeners) {
        try {
          l.accept(shard);
        } catch (RuntimeException ignored) {
          // best-effort notification; elector must never fail the application startup
        }
      }
    }
  }

  @Override
  public void stop() {
    started = false;
  }

  @Override
  public boolean isLeader(int shard) {
    return started && shards.contains(shard);
  }

  @Override
  public Set<Integer> leaderShards() {
    return started ? shards : Set.of();
  }

  @Override
  public OptionalLong leaderEpoch(int shard) {
    // Stable epoch for non-HA mode.
    return (started && shards.contains(shard)) ? OptionalLong.of(1L) : OptionalLong.empty();
  }

  @Override
  public void addListener(Consumer<Integer> listener) {
    listeners.add(listener);
  }
}
