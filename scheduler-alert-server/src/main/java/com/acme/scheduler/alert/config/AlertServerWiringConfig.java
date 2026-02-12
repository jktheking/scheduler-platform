package com.acme.scheduler.alert.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.acme.scheduler.alert.dispatch.AlertDispatcher;
import com.acme.scheduler.alert.dispatch.LoggingAlertDispatcher;
import com.acme.scheduler.alert.dispatch.WebhookAlertDispatcher;
import com.acme.scheduler.alert.kafka.KafkaAlertConsumerLoop;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(AlertServerKafkaProperties.class)
public class AlertServerWiringConfig {

  private static final Logger log = LoggerFactory.getLogger(AlertServerWiringConfig.class);

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }

  @Bean
  public KafkaConsumer<String, byte[]> alertConsumer(AlertServerKafkaProperties props) {
    Properties p = new Properties();
    p.put("bootstrap.servers", props.getKafka().getBootstrapServers());
    p.put("group.id", props.getKafka().getGroupId());
    p.put("client.id", props.getKafka().getClientId());
    p.put("enable.auto.commit", "false");
    p.put("auto.offset.reset", "earliest");
    p.put("key.deserializer", StringDeserializer.class.getName());
    p.put("value.deserializer", ByteArrayDeserializer.class.getName());
    return new KafkaConsumer<>(p);
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public AlertDispatcher alertDispatcher(AlertServerKafkaProperties props,
                                         ObjectMapper mapper,
                                         RestTemplate restTemplate) {
    List<AlertDispatcher> dispatchers = new ArrayList<>();
    dispatchers.add(new LoggingAlertDispatcher());

    if (props.getWebhook().getUrls() != null && !props.getWebhook().getUrls().isEmpty()) {
      dispatchers.add(new WebhookAlertDispatcher(props.getWebhook().getUrls(), mapper, restTemplate));
      log.info("checkpoint=alert.webhook_enabled urls_count={}", props.getWebhook().getUrls().size());
    }
    return AlertDispatcher.composite(dispatchers);
  }

  @Bean
  public KafkaAlertConsumerLoop kafkaAlertConsumerLoop(AlertServerKafkaProperties props,
                                                      KafkaConsumer<String, byte[]> alertConsumer,
                                                      AlertDispatcher dispatcher,
                                                      ObjectMapper mapper) {
    return new KafkaAlertConsumerLoop(props, alertConsumer, dispatcher, mapper);
  }

  @Bean
  public SmartLifecycle alertConsumerLifecycle(KafkaAlertConsumerLoop loop) {
    return new SmartLifecycle() {
      private volatile boolean running = false;
      @Override public void start() { loop.start(); running = true; }
      @Override public void stop() { loop.stop(); running = false; }
      @Override public boolean isRunning() { return running; }
      @Override public int getPhase() { return 0; }
    };
  }
}
