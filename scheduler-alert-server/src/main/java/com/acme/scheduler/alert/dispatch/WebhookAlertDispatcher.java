package com.acme.scheduler.alert.dispatch;

import com.acme.scheduler.common.alert.AlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;

/**
 * Optional webhook dispatcher. Failure is logged only (no alert DLQ).
 */
public final class WebhookAlertDispatcher implements AlertDispatcher {

  private static final Logger log = LoggerFactory.getLogger(WebhookAlertDispatcher.class);

  private final List<String> urls;
  private final ObjectMapper mapper;
  private final RestTemplate restTemplate;

  public WebhookAlertDispatcher(List<String> urls, ObjectMapper mapper, RestTemplate restTemplate) {
    this.urls = List.copyOf(Objects.requireNonNull(urls));
    this.mapper = Objects.requireNonNull(mapper);
    this.restTemplate = Objects.requireNonNull(restTemplate);
  }

  @Override
  public void dispatch(AlertEvent event) {
    if (event == null) return;
    try {
      String json = mapper.writeValueAsString(event);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> entity = new HttpEntity<>(json, headers);
      for (String url : urls) {
        try {
          restTemplate.postForEntity(url, entity, String.class);
        } catch (Exception e) {
          log.warn("checkpoint=alert.webhook_failed url={} eventId={} type={} workflowInstanceId={} err={}",
              url, event.eventId(), event.type(), event.workflowInstanceId(), e.toString());
        }
      }
    } catch (Exception e) {
      log.warn("checkpoint=alert.webhook_serialize_failed eventId={} err={}", event.eventId(), e.toString());
    }
  }
}
