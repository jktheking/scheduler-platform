package com.acme.scheduler.worker.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public final class ScriptTaskExecutor {

  public record ScriptResult(int exitCode, String stdout, String stderr) {}

  private final ObjectMapper mapper;

  public ScriptTaskExecutor(ObjectMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper);
  }

  public ScriptResult execute(String payloadJson) throws Exception {
    JsonNode root = mapper.readTree(payloadJson == null ? "{}" : payloadJson);
    String inline = root.path("inline").asText(null);
    String file = root.path("file").asText(null);
    String workingDir = root.path("workingDir").asText(null);
    long timeoutMs = root.path("timeoutMs").asLong(30_000);
    long maxOutputBytes = root.path("maxOutputBytes").asLong(16_384);

    Map<String, String> env = new HashMap<>();
    JsonNode envNode = root.path("env");
    if (envNode.isObject()) {
      envNode.fields().forEachRemaining(e -> env.put(e.getKey(), e.getValue().asText()));
    }

    Path scriptPath;
    boolean temp = false;
    if (inline != null && !inline.isBlank()) {
      scriptPath = Files.createTempFile("sched-script-", System.getProperty("os.name").toLowerCase().contains("win") ? ".cmd" : ".sh");
      Files.writeString(scriptPath, inline, StandardCharsets.UTF_8);
      temp = true;
    } else if (file != null && !file.isBlank()) {
      scriptPath = Path.of(file);
    } else {
      throw new IllegalArgumentException("SCRIPT payload must include 'inline' or 'file'");
    }

    if (!System.getProperty("os.name").toLowerCase().contains("win")) {
      try { scriptPath.toFile().setExecutable(true); } catch (Exception ignored) {}
    }

    List<String> cmd = new ArrayList<>();
    if (System.getProperty("os.name").toLowerCase().contains("win")) {
      cmd.add("cmd.exe");
      cmd.add("/c");
      cmd.add(scriptPath.toAbsolutePath().toString());
    } else {
      cmd.add("bash");
      cmd.add(scriptPath.toAbsolutePath().toString());
    }

    ProcessBuilder pb = new ProcessBuilder(cmd);
    if (workingDir != null && !workingDir.isBlank()) {
      pb.directory(new File(workingDir));
    }
    pb.environment().putAll(env);

    Process p = pb.start();

    BoundedBytes stdout = new BoundedBytes(maxOutputBytes);
    BoundedBytes stderr = new BoundedBytes(maxOutputBytes);

    Thread t1 = Thread.startVirtualThread(() -> pump(p.getInputStream(), stdout));
    Thread t2 = Thread.startVirtualThread(() -> pump(p.getErrorStream(), stderr));

    boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    if (!finished) {
      p.destroyForcibly();
      throw new RuntimeException("Script timed out after " + timeoutMs + "ms");
    }

    t1.join(Duration.ofSeconds(5));
    t2.join(Duration.ofSeconds(5));

    int exit = p.exitValue();
    if (temp) {
      try { Files.deleteIfExists(scriptPath); } catch (Exception ignored) {}
    }

    return new ScriptResult(exit, stdout.toStringUtf8(), stderr.toStringUtf8());
  }

  private static void pump(InputStream in, BoundedBytes buf) {
    try (in) {
      byte[] tmp = new byte[1024];
      int r;
      while ((r = in.read(tmp)) >= 0) {
        buf.write(tmp, 0, r);
        if (buf.isFull()) break;
      }
    } catch (Exception ignored) {
    }
  }

  private static final class BoundedBytes {
    private final long max;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    BoundedBytes(long max) { this.max = Math.max(0, max); }
    void write(byte[] b, int off, int len) {
      if (max == 0) return;
      long remaining = max - out.size();
      if (remaining <= 0) return;
      int toWrite = (int) Math.min(remaining, len);
      out.write(b, off, toWrite);
    }
    boolean isFull() { return max > 0 && out.size() >= max; }
    String toStringUtf8() { return out.toString(StandardCharsets.UTF_8); }
  }
}
