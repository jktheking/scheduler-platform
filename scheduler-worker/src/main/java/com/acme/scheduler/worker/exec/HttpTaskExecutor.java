package com.acme.scheduler.worker.exec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public final class HttpTaskExecutor {

  public record Result(int statusCode, String responseSnippet) {}

  private final HttpClient client;
  private final ObjectMapper mapper;

  public HttpTaskExecutor(ObjectMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper);
    this.client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  }

  public Result execute(String payloadJson) throws Exception {
    JsonNode root = mapper.readTree(payloadJson == null ? "{}" : payloadJson);
    String method = root.path("method").asText("GET");
    String url = root.path("url").asText();
    if (url == null || url.isBlank()) throw new IllegalArgumentException("url is required");
    long timeoutMs = root.path("timeoutMs").asLong(10_000);

    HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMillis(timeoutMs));

    JsonNode headers = root.path("headers");
    if (headers.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> it = headers.fields();
      while (it.hasNext()) {
        var e = it.next();
        b.header(e.getKey(), e.getValue().asText());
      }
    }

    String body = root.path("body").isMissingNode() ? null : root.path("body").asText(null);

    switch (method.toUpperCase()) {
      case "GET" -> b.GET();
      case "DELETE" -> b.DELETE();
      case "POST", "PUT", "PATCH" -> {
        if (body == null) body = "";
        b.method(method.toUpperCase(), HttpRequest.BodyPublishers.ofString(body));
      }
      default -> throw new IllegalArgumentException("Unsupported method " + method);
    }

    HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    String snippet = resp.body();
    if (snippet != null && snippet.length() > 512) snippet = snippet.substring(0, 512);
    return new Result(resp.statusCode(), snippet);
  }
}
