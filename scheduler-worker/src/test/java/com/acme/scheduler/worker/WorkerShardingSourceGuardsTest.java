package com.acme.scheduler.worker;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight source guards ensuring worker-side Kafka producer alignment remains available.
 *
 * <p>Worker does not elect shard leadership; it only produces task state keyed by workflow_instance_id.
 */
public class WorkerShardingSourceGuardsTest {

  @Test
  void shardPartitionerExistsAndIsWired() throws Exception {
    Path root = findRepoRoot().resolve("scheduler-worker/src/main/java");

    Path partitioner = root.resolve("com/acme/scheduler/worker/kafka/ShardKeyPartitioner.java");
    assertTrue(Files.exists(partitioner), "Worker ShardKeyPartitioner must exist");

    Path wiring = root.resolve("com/acme/scheduler/worker/config/WorkerWiringConfig.java");
    assertTrue(Files.exists(wiring), "WorkerWiringConfig must exist");
    String src = Files.readString(wiring, StandardCharsets.UTF_8);
    assertTrue(src.contains("ShardKeyPartitioner"), "WorkerWiringConfig should reference ShardKeyPartitioner");
  }

  private static Path findRepoRoot() {
    Path p = Path.of("").toAbsolutePath();
    while (p != null) {
      if (Files.exists(p.resolve("settings.gradle.kts"))) return p;
      p = p.getParent();
    }
    return Path.of("/mnt/data/work2");
  }
}
