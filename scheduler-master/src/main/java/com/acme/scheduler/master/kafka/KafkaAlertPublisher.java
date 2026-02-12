package com.acme.scheduler.master.kafka;

import com.acme.scheduler.common.alert.AlertEvent;
import com.acme.scheduler.domain.alert.AlertPublisher;
import com.acme.scheduler.master.config.MasterKafkaProperties;
import com.acme.scheduler.master.ha.ShardLeaderElector;
import com.acme.scheduler.master.sharding.ShardCalculator;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/** Publishes {@link AlertEvent} to the configured Kafka alerts topic. */
public final class KafkaAlertPublisher implements AlertPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaAlertPublisher.class);

  private final KafkaProducer<String, byte[]> producer;
  private final String topic;
  private final ObjectMapper mapper;
  private final MasterMetrics metrics;
  private final ShardLeaderElector shardElector;
  private final boolean shardingEnabled;
  private final ShardCalculator shardCalculator;

  public KafkaAlertPublisher(KafkaProducer<String, byte[]> producer,
                             MasterKafkaProperties props,
                             ObjectMapper mapper,
                             MasterMetrics metrics,
                             ShardLeaderElector shardElector,
                             ShardCalculator shardCalculator,
                             boolean shardingEnabled) {
    this.producer = Objects.requireNonNull(producer);
    this.topic = Objects.requireNonNull(props).getKafka().getAlertsTopic();
    this.mapper = Objects.requireNonNull(mapper);
    this.metrics = Objects.requireNonNull(metrics);
    this.shardElector = Objects.requireNonNull(shardElector);
    this.shardCalculator = Objects.requireNonNull(shardCalculator);
    this.shardingEnabled = shardingEnabled;
  }

  @Override
  public void publish(AlertEvent event) {
    if (event == null) return;

    // Defense-in-depth: alerts must be published by the elected leader only.
    if (!shardElector.isLeader(shardCalculator.shardForWorkflowInstanceId(event.workflowInstanceId()))) {
      return;
    }
    try {
      byte[] payload = mapper.writeValueAsBytes(event);
      ProducerRecord<String, byte[]> rec = new ProducerRecord<>(topic, Long.toString(event.workflowInstanceId()), payload);
      producer.send(rec, (meta, ex) -> {
        if (ex != null) {
          metrics.alertPublishError.add(1);
        } else {
          metrics.alertPublished.add(1);
        }
      });
    } catch (Exception e) {
      metrics.alertPublishError.add(1);
      log.warn("Failed to publish alert eventId={} type={} workflowInstanceId={}: {}",
          event.eventId(), event.type(), event.workflowInstanceId(), e.toString());
    }
  }
}
