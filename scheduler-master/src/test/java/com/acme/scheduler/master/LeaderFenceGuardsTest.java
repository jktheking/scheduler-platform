package com.acme.scheduler.master;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Defense-in-depth: ensure DB/Kafka mutating master components consult LeaderElector.
 *
 * <p>This is intentionally a lightweight source guard (not a behavioral test) to prevent
 * regressions where follower instances start mutating state under HA.
 */
public class LeaderFenceGuardsTest {

  @Test
  void mutatingComponentsContainLeaderChecks() throws Exception {
    Path root = findRepoRoot().resolve("scheduler-master").resolve("src").resolve("main").resolve("java");

    // Files that MUST contain a leader check.
    List<String> mustBeLeaderFiles = List.of(
        "com/acme/scheduler/master/runtime/DefaultDagRuntime.java",
        "com/acme/scheduler/master/runtime/TaskTimeoutReconciler.java",
        "com/acme/scheduler/master/runtime/TaskEnqueueReconciler.java",
        "com/acme/scheduler/master/runtime/MasterSchedulerLoop.java",
        "com/acme/scheduler/master/kafka/KafkaReadyPublisher.java",
        "com/acme/scheduler/master/kafka/KafkaAlertPublisher.java"
    );

    StringBuilder failures = new StringBuilder();
    for (String rel : mustBeLeaderFiles) {
      Path f = root.resolve(rel);
      if (!Files.exists(f)) {
        failures.append("Missing expected file: ").append(rel).append('\n');
        continue;
      }
      String src = Files.readString(f, StandardCharsets.UTF_8);
      boolean hasCheck =
          src.contains("shardElector") ||
          src.contains("leaderShards(") ||
          src.contains(".isLeader(") ||
          src.contains("elector.isLeader") ||
          src.contains("leaderElector.isLeader");

      if (!hasCheck) {
        failures.append("No leader fence detected in: ").append(rel).append('\n');
      }
    }

    if (failures.length() > 0) {
      fail("Leader fence guards failed:\n" + failures);
    }

    
    // Shard-scoped queries must include shard_id filters when sharding is enabled.
    List<String> mustContainShardId = List.of(
        "com/acme/scheduler/master/runtime/TaskEnqueueReconciler.java",
        "com/acme/scheduler/master/adapter/jdbc/JdbcTriggerRepository.java",
        "com/acme/scheduler/master/adapter/jdbc/JdbcDagTaskInstanceRepository.java"
    );
    for (String rel : mustContainShardId) {
      Path f = root.resolve(rel);
      if (!Files.exists(f)) continue;
      String src2 = Files.readString(f, StandardCharsets.UTF_8);
      assertTrue(src2.contains("shard_id"), "Expected shard_id usage in: " + rel);
    }
// Spot-check: KafkaCommandWriterLoop should not auto-start (it is started via leader-gated lifecycle).
    Path writer = root.resolve("com/acme/scheduler/master/kafka/KafkaCommandWriterLoop.java");
    if (Files.exists(writer)) {
      String src = Files.readString(writer, StandardCharsets.UTF_8);
      assertTrue(src.contains("isAutoStartup") && src.contains("return false"),
          "KafkaCommandWriterLoop must not auto-start; it should be started/stopped via leader gate.");
    }
  }

  private static Path findRepoRoot() {
    Path p = Path.of("").toAbsolutePath();
    while (p != null) {
      if (Files.exists(p.resolve("settings.gradle.kts"))) return p;
      p = p.getParent();
    }
    return Path.of("/mnt/data/repo/scheduler-platform");
  }
}
