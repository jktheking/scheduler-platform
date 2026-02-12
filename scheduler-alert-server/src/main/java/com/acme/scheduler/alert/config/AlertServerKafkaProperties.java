package com.acme.scheduler.alert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "scheduler.alert-server")
public class AlertServerKafkaProperties {

  private final Kafka kafka = new Kafka();
  private final Webhook webhook = new Webhook();

  public Kafka getKafka() { return kafka; }
  public Webhook getWebhook() { return webhook; }

  public static class Kafka {
    private String bootstrapServers = "kafka:29092";
    private String topic = "scheduler.alerts.v1";
    private String groupId = "scheduler-alert-server";
    private String clientId = "scheduler-alert-server";

    public String getBootstrapServers() { return bootstrapServers; }
    public void setBootstrapServers(String bootstrapServers) { this.bootstrapServers = bootstrapServers; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
  }

  public static class Webhook {
    /** Optional list of webhook URLs to POST AlertEvent JSON to. */
    private List<String> urls = new ArrayList<>();

    public List<String> getUrls() { return urls; }
    public void setUrls(List<String> urls) { this.urls = urls == null ? new ArrayList<>() : urls; }
  }
}
