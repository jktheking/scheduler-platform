package com.acme.scheduler.master.kafka;

import java.util.Objects;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.acme.scheduler.master.config.MasterKafkaProperties;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.runtime.TaskReadyEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class KafkaReadyPublisher {

  private final KafkaProducer<String, byte[]> producer;
  private final String topic;
  private final ObjectMapper mapper;
  private final MasterMetrics metrics;

  public KafkaReadyPublisher(KafkaProducer<String, byte[]> producer, MasterKafkaProperties props, ObjectMapper mapper, MasterMetrics metrics) {
    this.producer = Objects.requireNonNull(producer);
    this.topic = Objects.requireNonNull(props).getKafka().getReadyTopic();
    this.mapper = Objects.requireNonNull(mapper);
    this.metrics = Objects.requireNonNull(metrics);
  }

  public void publish(TaskReadyEvent evt) {
    try {
      byte[] payload = mapper.writeValueAsBytes(evt);
      ProducerRecord<String, byte[]> rec = new ProducerRecord<>(topic, Long.toString(evt.workflowInstanceId()), payload);
      producer.send(rec, (meta, ex) -> {
        if (ex != null) {
          metrics.readyPublishError.add(1);
        } else {
          metrics.readyPublished.add(1);
        }
      });
    } catch (Exception e) {
      metrics.readyPublishError.add(1);
      // swallow; master will retry via trigger status on next poll
    }
  }
}
