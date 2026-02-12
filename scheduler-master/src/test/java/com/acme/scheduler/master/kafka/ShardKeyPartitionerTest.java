package com.acme.scheduler.master.kafka;

import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ShardKeyPartitionerTest {

  private static Cluster clusterWithPartitions(String topic, int partitions) {
    List<PartitionInfo> pis = new ArrayList<>();
    for (int i = 0; i < partitions; i++) {
      pis.add(new PartitionInfo(topic, i, null, null, null));
    }
    Map<String, List<PartitionInfo>> parts = Map.of(topic, pis);
    return new Cluster("c", List.of(), pis, Set.of(), Set.of());
  }

  @Test
  void partition_isDeterministic_andWithinRange() {
    String topic = "scheduler.tasks.ready.v1";
    Cluster cluster = clusterWithPartitions(topic, 16);

    ShardKeyPartitioner p = new ShardKeyPartitioner();
    p.configure(Map.of());

    int a = p.partition(topic, "123", null, null, null, cluster);
    int b = p.partition(topic, "123", null, null, null, cluster);
    assertEquals(a, b);
    assertTrue(a >= 0 && a < 16);
  }

  @Test
  void partition_respectsShardHintButStillModByTopicPartitions() {
    String topic = "scheduler.tasks.ready.v1";
    Cluster cluster = clusterWithPartitions(topic, 8);

    ShardKeyPartitioner p = new ShardKeyPartitioner();
    p.configure(Map.of(ShardKeyPartitioner.CONF_SHARDS, 1024));

    int part = p.partition(topic, "999", null, null, null, cluster);
    assertTrue(part >= 0 && part < 8);
  }

  @Test
  void partition_handlesNullOrEmptyKey() {
    String topic = "scheduler.tasks.ready.v1";
    Cluster cluster = clusterWithPartitions(topic, 10);

    ShardKeyPartitioner p = new ShardKeyPartitioner();
    assertEquals(0, p.partition(topic, null, null, null, null, cluster));
    assertEquals(0, p.partition(topic, "", "".getBytes(), null, null, cluster));
  }
}
