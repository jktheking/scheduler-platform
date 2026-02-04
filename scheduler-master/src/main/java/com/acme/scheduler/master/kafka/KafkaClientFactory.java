package com.acme.scheduler.master.kafka;

import com.acme.scheduler.master.config.MasterKafkaProperties;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public final class KafkaClientFactory {

  private KafkaClientFactory() {}

  public static Properties base(MasterKafkaProperties props) {
    Properties p = new Properties();
    p.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, props.getKafka().getBootstrapServers());
    p.put(CommonClientConfigs.CLIENT_ID_CONFIG, props.getKafka().getClientId());
    return p;
  }

  public static Properties producerProps(MasterKafkaProperties props) {
    Properties p = base(props);
    p.put(ProducerConfig.ACKS_CONFIG, "all");
    p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    p.put(ProducerConfig.LINGER_MS_CONFIG, "5");
    p.put(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(64 * 1024));
    p.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
    p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    return p;
  }

  public static Properties consumerProps(MasterKafkaProperties props, String groupId) {
    Properties p = base(props);
    p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    return p;
  }
}
