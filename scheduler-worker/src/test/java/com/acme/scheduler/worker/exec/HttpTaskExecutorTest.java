package com.acme.scheduler.worker.exec;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the JDK HttpClient-based executor (no Spring context required). */
class HttpTaskExecutorTest {

  private com.sun.net.httpserver.HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) server.stop(0);
  }

  @Test
  void execute_get_success() throws Exception {
    server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/ping", ex -> {
      byte[] body = "pong".getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("Content-Type", "text/plain");
      ex.sendResponseHeaders(200, body.length);
      try (OutputStream os = ex.getResponseBody()) {
        os.write(body);
      }
    });
    server.start();
    String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

    var exec = new HttpTaskExecutor(new ObjectMapper());
    String payload = "{" +
        "\"method\":\"GET\"," +
        "\"url\":\"" + baseUrl + "/ping\"," +
        "\"timeoutMs\":5000" +
        "}";

    HttpTaskExecutor.Result r = exec.execute(payload);
    assertThat(r.statusCode()).isEqualTo(200);
    assertThat(r.responseSnippet()).contains("pong");
  }

  @Test
  void execute_requires_url() {
    var exec = new HttpTaskExecutor(new ObjectMapper());
    assertThatThrownBy(() -> exec.execute("{\"method\":\"GET\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url is required");
  }
}
