package com.acme.scheduler.master.kafka;

import java.util.Objects;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.acme.scheduler.master.config.MasterKafkaProperties;
import com.acme.scheduler.master.ha.ShardLeaderElector;
import com.acme.scheduler.master.sharding.ShardCalculator;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.runtime.TaskReadyEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class KafkaReadyPublisher {

  private final KafkaProducer<String, byte[]> producer;
  private final String topic;
  private final ObjectMapper mapper;
  private final MasterMetrics metrics;
  private final ShardLeaderElector shardElector;
  private final boolean shardingEnabled;
  private final ShardCalculator shardCalculator;

  public KafkaReadyPublisher(KafkaProducer<String, byte[]> producer,
                             MasterKafkaProperties props,
                             ObjectMapper mapper,
                             MasterMetrics metrics,
                             ShardLeaderElector shardElector,
                             ShardCalculator shardCalculator,
                             boolean shardingEnabled) {
    this.producer = Objects.requireNonNull(producer);
    this.topic = Objects.requireNonNull(props).getKafka().getReadyTopic();
    this.mapper = Objects.requireNonNull(mapper);
    this.metrics = Objects.requireNonNull(metrics);
    this.shardElector = Objects.requireNonNull(shardElector);
    this.shardCalculator = Objects.requireNonNull(shardCalculator);
    this.shardingEnabled = shardingEnabled;
  }

  public void publish(TaskReadyEvent evt) {
    // Defense-in-depth: never publish ready events from a follower.
    if (!shardElector.isLeader(shardCalculator.shardForWorkflowInstanceId(evt.workflowInstanceId()))) {
      return;
    }
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
