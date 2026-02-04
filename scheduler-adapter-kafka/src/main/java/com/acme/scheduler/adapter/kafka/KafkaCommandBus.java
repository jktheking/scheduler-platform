package com.acme.scheduler.adapter.kafka;

import java.util.Map;
import java.util.Properties;
import java.time.Duration;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.acme.scheduler.service.admission.SimpleCircuitBreaker;
import com.acme.scheduler.service.port.CommandBus;
import com.acme.scheduler.service.workflow.CommandEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Kafka-based CommandBus. Publishes commands so masters can consume without DB polling.
 *
 * - Idempotent producer enabled.
 * - Uses idempotencyKey as message key to preserve per-key ordering and enable log compaction if desired.
 */
public final class KafkaCommandBus implements CommandBus {

  private final KafkaProducer<String, byte[]> producer;
  private final String topic;
  private final ObjectMapper mapper;
  private final SimpleCircuitBreaker breaker;

  public KafkaCommandBus(
      String bootstrapServers,
      String topic,
      String clientId,
      ObjectMapper mapper,
      SimpleCircuitBreaker breaker
  ) {
    this.topic = topic;
    this.mapper = mapper;
    this.breaker = breaker;

    Properties p = new Properties();
    p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    p.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);

    // Durability + ordering
    p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    p.put(ProducerConfig.ACKS_CONFIG, "all");
    p.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(Integer.MAX_VALUE));
    p.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

    // Throughput
    p.put(ProducerConfig.LINGER_MS_CONFIG, "5");
    p.put(ProducerConfig.BATCH_SIZE_CONFIG, String.valueOf(128 * 1024));
    p.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

    // Timeouts
    p.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "120000");
    p.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "20000");

    this.producer = new KafkaProducer<>(p, new StringSerializer(), new ByteArraySerializer());
  }

  @Override
  public void publish(CommandEnvelope command) {
    if (!breaker.allowRequest()) {
      throw new IllegalStateException("Kafka circuit breaker open");
    }

    try {
      String key = command.idempotencyKey();
      byte[] value = mapper.writeValueAsBytes(command);

      // We keep this async for throughput; callback updates breaker state.
      producer.send(new ProducerRecord<>(topic, key, value), new Callback() {
        @Override public void onCompletion(RecordMetadata metadata, Exception exception) {
          if (exception == null) {
            breaker.onSuccess();
          } else {
            breaker.onFailure();
          }
        }
      });
    } catch (Exception e) {
      breaker.onFailure();
      throw new RuntimeException("KafkaCommandBus publish failed", e);
    }
  }

  /**
   * Best-effort pressure signal source for admission control.
   * Exposes producer metrics without forcing Kafka deps into core.
   */
  public Map<MetricName, ? extends Metric> metrics() {
    return producer.metrics();
  }

  public void flush(long timeoutMillis) {
    producer.flush();
    // KafkaProducer#flush has no timeout; callers can enforce externally.
  }

  public void close() {
    producer.close(Duration.ofSeconds(10));
  }
}
