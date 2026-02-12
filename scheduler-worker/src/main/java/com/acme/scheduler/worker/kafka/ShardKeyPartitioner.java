package com.acme.scheduler.worker.kafka;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Same mapping as master shard calculator, but implemented here to avoid sharing Kafka
 * dependencies across modules.
 */
public final class ShardKeyPartitioner implements Partitioner {

  public static final String CONF_SHARDS = "scheduler.kafka.shards";

  private int shardsHint = 0;

  @Override
  public void configure(Map<String, ?> configs) {
    Object v = configs.get(CONF_SHARDS);
    if (v instanceof String s) {
      try { shardsHint = Integer.parseInt(s); } catch (Exception ignored) {}
    } else if (v instanceof Number n) {
      shardsHint = n.intValue();
    }
    if (shardsHint < 0) shardsHint = 0;
  }

  @Override
  public int partition(String topic, Object keyObj, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
    int numPartitions = cluster.partitionCountForTopic(topic);
    if (numPartitions <= 1) return 0;

    if (keyBytes == null && keyObj != null) {
      keyBytes = keyObj.toString().getBytes(StandardCharsets.UTF_8);
    }
    if (keyBytes == null || keyBytes.length == 0) {
      return 0;
    }

    String k = new String(keyBytes, StandardCharsets.UTF_8);
    long asLong = tryParseLong(k);

    int base = (shardsHint > 0) ? shardsHint : numPartitions;
    int shard = shardForLong(asLong, base);
    return Math.floorMod(shard, numPartitions);
  }

  private static long tryParseLong(String s) {
    try { return Long.parseLong(s); } catch (Exception ignore) {
      long h = 1125899906842597L;
      for (int i = 0; i < s.length(); i++) h = 31 * h + s.charAt(i);
      return h;
    }
  }

  private static int shardForLong(long v, int shards) {
    long h = mix64(v);
    return (int) Math.floorMod(h, shards);
  }

  private static long mix64(long z) {
    z += 0x9E3779B97F4A7C15L;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }

  @Override public void close() {}
}
