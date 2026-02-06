package com.acme.scheduler.master.kafka;

import com.acme.scheduler.common.runtime.TaskStateEvent;
import com.acme.scheduler.master.config.MasterKafkaProperties;
import com.acme.scheduler.master.runtime.DagRuntime;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KafkaTaskStateConsumerLoop implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(KafkaTaskStateConsumerLoop.class);

  private final MasterKafkaProperties props;
  private final KafkaConsumer<String, byte[]> consumer;
  private final DagRuntime dagRuntime;
  private final ObjectMapper mapper;
  private final MasterMetrics metrics;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread thread;

  public KafkaTaskStateConsumerLoop(MasterKafkaProperties props,
                                   KafkaConsumer<String, byte[]> consumer,
                                   DagRuntime dagRuntime,
                                   ObjectMapper mapper,
                                   MasterMetrics metrics) {
    this.props = Objects.requireNonNull(props);
    this.consumer = Objects.requireNonNull(consumer);
    this.dagRuntime = Objects.requireNonNull(dagRuntime);
    this.mapper = Objects.requireNonNull(mapper);
    this.metrics = Objects.requireNonNull(metrics);
  }

  public void start() {
    if (!running.compareAndSet(false, true)) return;
    consumer.subscribe(java.util.List.of(props.getKafka().getTaskStateTopic()));
    thread = new Thread(this, "kafka-task-state-consumer");
    thread.setDaemon(true);
    thread.start();
  }

  public void stop() {
    running.set(false);
    consumer.wakeup();
  }

  @Override
  public void run() {
    try {
      while (running.get()) {
        ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(200));
        records.forEach(r -> {
          try {
            TaskStateEvent evt = mapper.readValue(r.value(), TaskStateEvent.class);
            log.info("checkpoint=master.task_state_consumed workflowInstanceId={} taskInstanceId={} attempt={} state={} topic={} partition={} offset={}",
                evt.workflowInstanceId(), evt.taskInstanceId(), evt.attempt(), evt.state(), r.topic(), r.partition(), r.offset());
            dagRuntime.onTaskState(evt);
          } catch (Exception e) {
            log.warn("Failed to parse TaskStateEvent: {}", e.toString());
          }
        });
        consumer.commitAsync();
      }
    } catch (WakeupException we) {
      // normal
    } catch (Exception e) {
      log.warn("KafkaTaskStateConsumerLoop error: {}", e.toString());
    } finally {
      try { consumer.close(); } catch (Exception ignored) {}
    }
  }
}
