package com.acme.scheduler.worker.exec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for script execution.
 *
 * <p>Uses inline scripts to avoid any external files.
 */
class ScriptTaskExecutorTest {

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC}) // Worker runtime assumption: POSIX shell available
  void execute_inline_script_success_and_env() throws Exception {
    var mapper = new ObjectMapper();
    var exec = new ScriptTaskExecutor(mapper);

    // Use sh for portability (bash may not exist everywhere).
    var payloadNode = mapper.createObjectNode();
    payloadNode.put(
        "inline",
        "#!/usr/bin/env sh\n"
            + "echo hello\n"
            + "echo env=$MY_ENV\n"
            + "exit 0\n");
    payloadNode.put("timeoutMs", 5000);
    payloadNode.put("maxOutputBytes", 16384);
    payloadNode.putObject("env").put("MY_ENV", "world");

    String payload = mapper.writeValueAsString(payloadNode);

    ScriptTaskExecutor.ScriptResult r = exec.execute(payload);
    assertThat(r.exitCode()).isEqualTo(0);
    assertThat(r.stdout()).contains("hello");
    assertThat(r.stdout()).contains("env=world");
  }

  @Test
  void execute_requires_inline_or_file() {
    var exec = new ScriptTaskExecutor(new ObjectMapper());
    assertThatThrownBy(() -> exec.execute("{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inline")
        .hasMessageContaining("file");
  }
}
