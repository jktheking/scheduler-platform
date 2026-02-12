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
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guardrails to prevent accidental clean-architecture violations.
 *
 * <p>Rule: only scheduler-master and scheduler-worker may mutate runtime tables:
 * t_workflow_instance, t_task_instance, t_trigger, t_dlq_task.
 *
 * <p>Other modules may query these tables, and may mutate WAL tables (t_command, t_command_dedupe).
 */
public class ArchitectureGuardsTest {

  private static final Pattern FORBIDDEN_MUTATION = Pattern.compile(
      "(?is)" +
          "(\\binsert\\s+into\\s+t_(workflow_instance|task_instance|trigger|dlq_task)\\b)" +
          "|" +
          "(\\bupdate\\s+t_(workflow_instance|task_instance|trigger|dlq_task)\\b)" +
          "|" +
          "(\\bdelete\\s+from\\s+t_(workflow_instance|task_instance|trigger|dlq_task)\\b)"
  );

  @Test
  void runtimeTablesAreMutatedOnlyByMasterOrWorker() throws Exception {
    Path root = findRepoRoot();

    // Only scan Java sources and SQL migrations (where violations are most likely to appear).
    List<Path> scanRoots = List.of(
        root.resolve("scheduler-api"),
        root.resolve("scheduler-service"),
        root.resolve("scheduler-domain"),
        root.resolve("scheduler-adapter-jdbc"),
        root.resolve("scheduler-adapter-kafka"),
        root.resolve("scheduler-adapter-inmemory"),
        root.resolve("scheduler-remote"),
        root.resolve("scheduler-alert-server"),
        root.resolve("scheduler-task-http"),
        root.resolve("scheduler-task-script")
    );

    List<String> violations = new ArrayList<>();
    for (Path r : scanRoots) {
      if (!Files.exists(r)) continue;
      walk(r, violations);
    }

    if (!violations.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("Found forbidden runtime-table mutations outside scheduler-master/scheduler-worker:\n\n");
      for (String v : violations) sb.append(" - ").append(v).append('\n');
      sb.append("\nFix: route these mutations through the WAL (Kafka/DB) and apply them in the master leader.");
      fail(sb.toString());
    }
  }

  private static void walk(Path root, List<String> violations) throws IOException {
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".java") || name.endsWith(".sql"))) return FileVisitResult.CONTINUE;

        // Ignore build output.
        String p = file.toString().replace('\\', '/');
        if (p.contains("/build/") || p.contains("/bin/") || p.contains("/.gradle/")) {
          return FileVisitResult.CONTINUE;
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (FORBIDDEN_MUTATION.matcher(content).find()) {
          violations.add(p);
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static Path findRepoRoot() {
    Path p = Path.of("").toAbsolutePath();
    while (p != null) {
      if (Files.exists(p.resolve("settings.gradle.kts"))) return p;
      p = p.getParent();
    }
    // Fallback: tests should still run even if executed from an unusual working dir.
    return Path.of("/mnt/data/repo/scheduler-platform");
  }
}
