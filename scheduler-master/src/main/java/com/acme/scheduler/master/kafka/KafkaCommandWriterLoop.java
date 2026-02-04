package com.acme.scheduler.master.kafka;

import com.acme.scheduler.master.config.MasterKafkaProperties;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.adapter.jdbc.JdbcCommandWriter;
import com.acme.scheduler.service.workflow.CommandEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Consumer group: scheduler-command-writer
 * Reads scheduler.commands.v1 and persists to Postgres t_command with ON CONFLICT DO NOTHING.
 */
public final class KafkaCommandWriterLoop implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(KafkaCommandWriterLoop.class);

  private final MasterKafkaProperties props;
  private final KafkaConsumer<String, byte[]> consumer;
  private final JdbcCommandWriter writer;
  private final ObjectMapper mapper;
  private final MasterMetrics metrics;

  private final ExecutorService loop = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r);
    t.setName("kafka-command-writer");
    t.setDaemon(true);
    return t;
  });

  private volatile boolean running = false;

  public KafkaCommandWriterLoop(MasterKafkaProperties props,
                               KafkaConsumer<String, byte[]> consumer,
                               JdbcCommandWriter writer,
                               ObjectMapper mapper,
                               MasterMetrics metrics) {
    this.props = Objects.requireNonNull(props);
    this.consumer = Objects.requireNonNull(consumer);
    this.writer = Objects.requireNonNull(writer);
    this.mapper = Objects.requireNonNull(mapper);
    this.metrics = Objects.requireNonNull(metrics);
  }

  @Override
  public void start() {
    if (running) return;
    running = true;
    consumer.subscribe(List.of(props.getKafka().getCommandsTopic()));
    loop.execute(this::runLoop);
    log.info("KafkaCommandWriterLoop started topic={} groupId={}", props.getKafka().getCommandsTopic(), props.getKafka().getWriterGroupId());
  }

  @Override
  public void stop() {
    running = false;
    try { consumer.wakeup(); } catch (Exception ignored) {}
    loop.shutdownNow();
    try { consumer.close(); } catch (Exception ignored) {}
    log.info("KafkaCommandWriterLoop stopped");
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return 0;
  }

  private void runLoop() {
    while (running) {
      try {
        var records = consumer.poll(Duration.ofMillis(200));
        for (ConsumerRecord<String, byte[]> rec : records) {
          CommandEnvelope cmd = mapper.readValue(rec.value(), CommandEnvelope.class);
          writer.insertIfAbsent(cmd);
        }
        consumer.commitSync();
      } catch (org.apache.kafka.common.errors.WakeupException we) {
        // shutdown
      } catch (Exception e) {
        metrics.commandKafkaError.add(1);
        log.warn("KafkaCommandWriterLoop error: {}", e.toString());
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
      }
    }
  }
}
