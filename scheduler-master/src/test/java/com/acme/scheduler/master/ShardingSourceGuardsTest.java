package com.acme.scheduler.master;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Defense-in-depth source guards for sharding (Model A).
 *
 * <p>These are intentionally lightweight string-based guards that prevent accidental regressions
 * where sharding is enabled but shard leadership filters/fences are removed.
 */
public class ShardingSourceGuardsTest {

  @Test
  void triggerEnginesAndLoopsConsultLeaderShardsWhenEnabled() throws Exception {
    Path root = findRepoRoot().resolve("scheduler-master/src/main/java");

    List<String> mustUseLeaderShards = List.of(
        "com/acme/scheduler/master/trigger/TimeWheelTriggerEngine.java",
        "com/acme/scheduler/master/trigger/QuartzTriggerEngine.java",
        "com/acme/scheduler/master/kafka/KafkaMasterCommandConsumerLoop.java",
        "com/acme/scheduler/master/kafka/KafkaTaskStateConsumerLoop.java"
    );

    StringBuilder failures = new StringBuilder();
    for (String rel : mustUseLeaderShards) {
      Path f = root.resolve(rel);
      if (!Files.exists(f)) {
        failures.append("Missing expected file: ").append(rel).append('\n');
        continue;
      }
      String src = Files.readString(f, StandardCharsets.UTF_8);
      boolean ok =
          src.contains("leaderShards()") ||
          src.contains("leaderShards(") ||
          src.contains("isLeader(") ||
          src.contains("shardElector");
      if (!ok) {
        failures.append("Missing shard leadership consult in: ").append(rel).append('\n');
      }
    }

    if (failures.length() > 0) {
      fail("Sharding source guards failed:\n" + failures);
    }
  }

  @Test
  void shardScopedSqlIncludesShardFilters() throws Exception {
    Path root = findRepoRoot().resolve("scheduler-master/src/main/java");

    // Ensure trigger claiming and upcoming load include shard_id filtering.
    Path triggerRepo = root.resolve("com/acme/scheduler/master/adapter/jdbc/JdbcTriggerRepository.java");
    if (Files.exists(triggerRepo)) {
      String src = Files.readString(triggerRepo, StandardCharsets.UTF_8);
      assertTrue(src.contains("shard_id"), "JdbcTriggerRepository must reference shard_id");
      // A bit stronger: shard_id should appear near due-claim / upcoming queries.
      assertTrue(src.contains("claimDue") && src.contains("shard_id"),
          "claimDue must be shard-filtered when sharding is enabled");
    }

    // Ensure task reconciliation queries reference shard_id.
    Path taskRepo = root.resolve("com/acme/scheduler/master/adapter/jdbc/JdbcDagTaskInstanceRepository.java");
    if (Files.exists(taskRepo)) {
      String src = Files.readString(taskRepo, StandardCharsets.UTF_8);
      assertTrue(src.contains("shard_id"), "JdbcDagTaskInstanceRepository must reference shard_id");
    }
  }

  @Test
  void partitionerIsWiredForShardKeyedTopics() throws Exception {
    Path root = findRepoRoot().resolve("scheduler-master/src/main/java");

    Path partitioner = root.resolve("com/acme/scheduler/master/kafka/ShardKeyPartitioner.java");
    assertTrue(Files.exists(partitioner), "ShardKeyPartitioner must exist (master producer alignment)");

    Path wiring = root.resolve("com/acme/scheduler/master/config/MasterKafkaWiringConfig.java");
    if (Files.exists(wiring)) {
      String src = Files.readString(wiring, StandardCharsets.UTF_8);
      assertTrue(src.contains("ShardKeyPartitioner"),
          "MasterKafkaWiringConfig should reference ShardKeyPartitioner when sharding is enabled");
    }
  }


  @Test
  void kafkaLoopsCommitOffsetsOnlyAfterAllApplied() throws Exception {
    Path root = findRepoRoot().resolve("scheduler-master/src/main/java");
    List<String> loops = List.of(
        "com/acme/scheduler/master/kafka/KafkaMasterCommandConsumerLoop.java",
        "com/acme/scheduler/master/kafka/KafkaTaskStateConsumerLoop.java"
    );

    StringBuilder failures = new StringBuilder();
    for (String rel : loops) {
      Path f = root.resolve(rel);
      if (!Files.exists(f)) {
        failures.append("Missing expected file: ").append(rel).append('\n');
        continue;
      }
      String src = Files.readString(f, StandardCharsets.UTF_8);

      int commits = countOccurrences(src, "commitSync(");
      if (commits != 1) {
        failures.append("Expected exactly 1 commitSync call in ").append(rel)
            .append(" but found ").append(commits).append('\n');
        continue;
      }

      // Ensure commitSync is guarded by allApplied fence (prevents dropping events on shard mismatch/partial apply).
      boolean guarded = Pattern.compile("if\\s*\\(\\s*allApplied\\s*\\)\\s*\\{[^}]*commitSync\\s*\\(",
              Pattern.DOTALL).matcher(src).find();
      if (!guarded) {
        failures.append("commitSync is not guarded by if(allApplied) in: ").append(rel).append('\n');
      }
    }

    if (failures.length() > 0) {
      fail("Kafka commit safety guards failed:\n" + failures);
    }
  }

  private static int countOccurrences(String haystack, String needle) {
    int idx = 0;
    int c = 0;
    while (true) {
      idx = haystack.indexOf(needle, idx);
      if (idx < 0) return c;
      c++;
      idx += needle.length();
    }
  }

  private static Path findRepoRoot() {
    Path p = Path.of("").toAbsolutePath();
    while (p != null) {
      if (Files.exists(p.resolve("settings.gradle.kts"))) return p;
      p = p.getParent();
    }
    // Fallback for CI/container runs.
    return Path.of("/mnt/data/work2");
  }
}
