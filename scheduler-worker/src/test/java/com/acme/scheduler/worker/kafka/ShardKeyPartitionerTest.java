package com.acme.scheduler.worker.kafka;

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
    return new Cluster("c", List.of(), pis, Set.of(), Set.of());
  }

  @Test
  void partition_isDeterministic_andWithinRange() {
    String topic = "scheduler.task.state.v1";
    Cluster cluster = clusterWithPartitions(topic, 12);

    ShardKeyPartitioner p = new ShardKeyPartitioner();
    p.configure(Map.of());

    int a = p.partition(topic, "123", null, null, null, cluster);
    int b = p.partition(topic, "123", null, null, null, cluster);
    assertEquals(a, b);
    assertTrue(a >= 0 && a < 12);
  }
}
