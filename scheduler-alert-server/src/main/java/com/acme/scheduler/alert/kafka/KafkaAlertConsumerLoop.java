package com.acme.scheduler.alert.kafka;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.acme.scheduler.alert.config.AlertServerKafkaProperties;
import com.acme.scheduler.alert.dispatch.AlertDispatcher;
import com.acme.scheduler.common.alert.AlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class KafkaAlertConsumerLoop implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(KafkaAlertConsumerLoop.class);

  private final AlertServerKafkaProperties props;
  private final KafkaConsumer<String, byte[]> consumer;
  private final AlertDispatcher dispatcher;
  private final ObjectMapper mapper;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread thread;

  public KafkaAlertConsumerLoop(AlertServerKafkaProperties props,
                               KafkaConsumer<String, byte[]> consumer,
                               AlertDispatcher dispatcher,
                               ObjectMapper mapper) {
    this.props = Objects.requireNonNull(props);
    this.consumer = Objects.requireNonNull(consumer);
    this.dispatcher = Objects.requireNonNull(dispatcher);
    this.mapper = Objects.requireNonNull(mapper);
  }

  public void start() {
    if (!running.compareAndSet(false, true)) return;
    consumer.subscribe(List.of(props.getKafka().getTopic()));
    thread = new Thread(this, "kafka-alert-consumer");
    thread.setDaemon(true);
    thread.start();
    log.info("checkpoint=alert.consumer_started topic={} groupId={}", props.getKafka().getTopic(), props.getKafka().getGroupId());
  }

  public void stop() {
    running.set(false);
    consumer.wakeup();
  }

  @Override
  public void run() {
    try {
      while (running.get()) {
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(250));
        records.forEach(r -> {
          try {
            AlertEvent evt = mapper.readValue(r.value(), AlertEvent.class);
            dispatcher.dispatch(evt);
          } catch (Exception e) {
            log.warn("checkpoint=alert.consume_error topic={} partition={} offset={} err={}", r.topic(), r.partition(), r.offset(), e.toString());
          }
        });
        consumer.commitAsync();
      }
    } catch (WakeupException we) {
      // normal shutdown
    } catch (Exception e) {
      log.warn("KafkaAlertConsumerLoop error: {}", e.toString());
    } finally {
      try { consumer.close(); } catch (Exception ignored) {}
    }
  }
}
