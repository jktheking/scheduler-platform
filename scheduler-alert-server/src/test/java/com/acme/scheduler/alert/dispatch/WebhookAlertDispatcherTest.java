package com.acme.scheduler.alert.dispatch;

import com.acme.scheduler.common.alert.AlertEvent;
import com.acme.scheduler.common.alert.AlertSeverity;
import com.acme.scheduler.common.alert.AlertType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WebhookAlertDispatcherTest {

  @Test
  void dispatch_posts_json_to_webhook() {
    RestTemplate rt = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();

    String url = "http://example.test/webhook";
    server.expect(requestTo(url))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"eventId\":\"e1\"")))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

    var d = new WebhookAlertDispatcher(List.of(url), new ObjectMapper().registerModule(new JavaTimeModule()), rt);

    AlertEvent e = new AlertEvent(
        "e1",
        AlertType.DLQ_CREATED,
        AlertSeverity.ERROR,
        "default",
        10L,
        123L,
        1,
        99L,
        910050L,
        0,
        null,
        Instant.now(),
        "t",
        "detail",
        null
    );

    d.dispatch(e);
    server.verify();
  }

  @Test
  void dispatch_logs_and_continues_when_one_webhook_fails() {
    var rt = org.mockito.Mockito.mock(RestTemplate.class);
    var mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    String good = "http://good.test/webhook";
    String bad = "http://bad.test/webhook";

    org.mockito.Mockito.when(rt.postForEntity(
            org.mockito.Mockito.eq(good),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.eq(String.class)))
        .thenReturn(new org.springframework.http.ResponseEntity<>("ok", org.springframework.http.HttpStatus.OK));

    org.mockito.Mockito.when(rt.postForEntity(
            org.mockito.Mockito.eq(bad),
            org.mockito.Mockito.any(),
            org.mockito.Mockito.eq(String.class)))
        .thenThrow(new RuntimeException("boom"));

    var d = new WebhookAlertDispatcher(List.of(good, bad), mapper, rt);

    AlertEvent e = new AlertEvent(
        "e2",
        AlertType.DLQ_CREATED,
        AlertSeverity.ERROR,
        "default",
        11L,
        124L,
        1,
        99L,
        910050L,
        0,
        null,
        Instant.now(),
        "t",
        "detail",
        null
    );

    // should not throw
    d.dispatch(e);

    org.mockito.Mockito.verify(rt).postForEntity(org.mockito.Mockito.eq(good), org.mockito.Mockito.any(), org.mockito.Mockito.eq(String.class));
    org.mockito.Mockito.verify(rt).postForEntity(org.mockito.Mockito.eq(bad), org.mockito.Mockito.any(), org.mockito.Mockito.eq(String.class));
  }
}
