package com.acme.scheduler.worker.kafka;

import com.acme.scheduler.common.runtime.TaskDispatchEnvelope;
import com.acme.scheduler.meter.SchedulerMeter;
import com.acme.scheduler.worker.runtime.WorkerTaskOrchestrator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KafkaReadyTaskConsumerLoop implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(KafkaReadyTaskConsumerLoop.class);

  private final WorkerKafkaProperties props;
  private final KafkaConsumer<String, byte[]> consumer;
  private final ObjectMapper mapper;
  private final WorkerTaskOrchestrator orchestrator;
  private final SchedulerMeter.Counter consumed;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread thread;

  public KafkaReadyTaskConsumerLoop(WorkerKafkaProperties props,
                                   KafkaConsumer<String, byte[]> consumer,
                                   ObjectMapper mapper,
                                   WorkerTaskOrchestrator orchestrator,
                                   SchedulerMeter meter) {
    this.props = Objects.requireNonNull(props);
    this.consumer = Objects.requireNonNull(consumer);
    this.mapper = Objects.requireNonNull(mapper);
    this.orchestrator = Objects.requireNonNull(orchestrator);
    this.consumed = meter.counter("scheduler.worker.ready.consumed.count", "Ready events consumed");
  }

  public void start() {
    if (!running.compareAndSet(false, true)) return;
    consumer.subscribe(List.of(props.getKafka().getReadyTopic()));
    log.info("KafkaReadyTaskConsumerLoop started topic={} groupId={} workerId={}", props.getKafka().getReadyTopic(), props.getKafka().getGroupId(), props.getWorkerId());
    thread = new Thread(this, "kafka-ready-consumer");
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
                boolean ok = true;
        for (var rec : records) {
          consumed.add(1);
          try {
            JsonNode node = mapper.readTree(rec.value());
            String payloadJson = node.path("payloadJson").asText("{}");
            TaskDispatchEnvelope env = mapper.readValue(payloadJson, TaskDispatchEnvelope.class);
            log.info("checkpoint=worker.ready_consumed workflowInstanceId={} taskInstanceId={} taskType={} attempt={} workerId={} topic={} partition={} offset={}",
                env.workflowInstanceId(), env.taskInstanceId(), env.taskType(), env.attempt(), props.getWorkerId(), rec.topic(), rec.partition(), rec.offset());
            ok = ok && orchestrator.handle(env, props.getWorkerId());
          } catch (Exception e) {
            ok = false;
            log.error("checkpoint=worker.ready_process_failed workerId={} topic={} partition={} offset={} error={}", props.getWorkerId(), rec.topic(), rec.partition(), rec.offset(), e.toString());
          }
        }
        if (ok && !records.isEmpty()) {
          consumer.commitSync();
        }
      }
    } catch (WakeupException we) {
      // normal
    } catch (Exception e) {
      log.warn("KafkaReadyTaskConsumerLoop error: {}", e.toString());
    } finally {
      try { consumer.close(); } catch (Exception ignored) {}
    }
  }
}
