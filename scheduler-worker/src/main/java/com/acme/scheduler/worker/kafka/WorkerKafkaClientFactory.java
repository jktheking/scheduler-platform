package com.acme.scheduler.worker.kafka;

import java.util.Properties;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

/**
 * Helper to build Kafka client Properties.
 */
public final class WorkerKafkaClientFactory {
  private WorkerKafkaClientFactory() {}

  public static Properties consumerProps(WorkerKafkaProperties props) {
    Properties p = new Properties();
    p.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, props.getKafka().getBootstrapServers());
    p.put(ConsumerConfig.GROUP_ID_CONFIG, props.getKafka().getGroupId());
    p.put(ConsumerConfig.CLIENT_ID_CONFIG, props.getKafka().getClientId());
    p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "200");
    return p;
  }

  public static Properties producerProps(WorkerKafkaProperties props) {
    Properties p = new Properties();
    p.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, props.getKafka().getBootstrapServers());
    p.put(ProducerConfig.ACKS_CONFIG, "all");
    p.put(ProducerConfig.LINGER_MS_CONFIG, "1");
    p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    return p;
  }
}
