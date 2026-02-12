package com.acme.scheduler.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guardrails to keep Kafka topic publishing/ownership disciplined under HA.
 *
 * <p>These checks are intentionally conservative and string-based:
 * they prevent accidental wiring where a new module starts publishing to a topic
 * that must remain owned by master/worker/alert-server.
 *
 * <p>Docs and demo folders are intentionally excluded.
 */
public class KafkaTopicGuardsTest {

  private static final Map<String, List<String>> TOPIC_ALLOWED_MODULES = Map.of(
      // Ready queue: master publishes, worker consumes.
      "scheduler.tasks.ready.v1", List.of("scheduler-master", "scheduler-worker", "scheduler-common"),

      // Worker state: worker publishes, master consumes.
      "scheduler.task.state.v1", List.of("scheduler-master", "scheduler-worker", "scheduler-common"),

      // Alerts: master publishes, alert-server consumes.
      "scheduler.alerts.v1", List.of("scheduler-master", "scheduler-alert-server", "scheduler-common"),

      // Command WAL: api publishes, master consumes (and adapter-kafka contains shared plumbing).
      "scheduler.commands.v1", List.of("scheduler-api", "scheduler-master", "scheduler-adapter-kafka", "scheduler-common")
  );

  @Test
  void restrictedTopicsAreReferencedOnlyInAllowedModules() throws Exception {
    Path root = findRepoRoot();

    List<String> violations = new ArrayList<>();

    for (var e : TOPIC_ALLOWED_MODULES.entrySet()) {
      String topic = e.getKey();
      List<String> allowed = e.getValue();

      for (Path moduleDir : listModuleDirs(root)) {
        String moduleName = moduleDir.getFileName().toString();
        if (allowed.contains(moduleName)) continue;

        // Exclude docs/demo by construction: we only iterate module dirs.
        scanModuleForTopic(moduleDir, moduleName, topic, violations);
      }
    }

    if (!violations.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("Found restricted Kafka topic references in disallowed modules:\n\n");
      for (String v : violations) sb.append(" - ").append(v).append('\n');
      sb.append("\nFix: keep topic ownership limited to the allowed modules, and route actions via the WAL when needed.");
      fail(sb.toString());
    }
  }

  private static List<Path> listModuleDirs(Path root) throws IOException {
    List<Path> out = new ArrayList<>();
    try (var s = Files.list(root)) {
      s.filter(Files::isDirectory)
          .filter(p -> p.getFileName().toString().startsWith("scheduler-"))
          // Ignore build outputs / tooling dirs
          .filter(p -> !p.getFileName().toString().equals("scheduler-platform"))
          .forEach(out::add);
    }
    return out;
  }

  private static void scanModuleForTopic(Path moduleDir, String moduleName, String topic, List<String> violations) throws IOException {
    Files.walkFileTree(moduleDir, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        // Normalize Windows paths for stable matching and ignore build outputs.
        String p = file.toString().replace('\\', '/');
        if (p.contains("/build/") || p.contains("/bin/") || p.contains("/.gradle/")) {
          return FileVisitResult.CONTINUE;
        }

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        // Only scan code/config where topics are likely declared.
        if (!(name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".kts") ||
            name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties"))) {
          return FileVisitResult.CONTINUE;
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);

        // Ignore mentions in comments/Javadoc; we only want to catch real wiring/config.
        if (name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".kts")) {
          content = stripComments(content);
        }

        if (content.contains(topic)) {
          violations.add(moduleName + " references '" + topic + "' in " + p);
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static String stripComments(String in) {
    // Very small, conservative stripper: removes /* */ blocks and // line comments.
    String noBlock = in.replaceAll("(?s)/\\*.*?\\*/", " ");
    return noBlock.replaceAll("(?m)//.*$", " ");
  }

  private static Path findRepoRoot() {
    Path p = Path.of("").toAbsolutePath();
    while (p != null) {
      if (Files.exists(p.resolve("settings.gradle.kts"))) return p;
      p = p.getParent();
    }
    // Fallback for unusual runners.
    return Path.of("/mnt/data/repo/scheduler-platform");
  }
}
