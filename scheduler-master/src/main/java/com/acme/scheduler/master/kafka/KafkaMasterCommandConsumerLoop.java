package com.acme.scheduler.master.kafka;

import com.acme.scheduler.master.adapter.jdbc.JdbcCommandDedupeRepository;
import com.acme.scheduler.master.config.MasterKafkaProperties;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.port.WorkflowScheduler;
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
 * Consumer group: scheduler-master
 * Reads scheduler.commands.v1 and materializes workflow instances/plans/triggers in DB.
 * Does NOT manage a DB "ready queue" – ready events are emitted by trigger engines to Kafka.
 */
public final class KafkaMasterCommandConsumerLoop implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(KafkaMasterCommandConsumerLoop.class);

  private final MasterKafkaProperties props;
  private final KafkaConsumer<String, byte[]> consumer;
  private final JdbcCommandDedupeRepository dedupe;
  private final WorkflowScheduler workflowScheduler;
  private final ObjectMapper mapper;
  private final MasterMetrics metrics;

  private final ExecutorService loop = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r);
    t.setName("kafka-master-consumer");
    t.setDaemon(true);
    return t;
  });

  private volatile boolean running = false;

  public KafkaMasterCommandConsumerLoop(MasterKafkaProperties props,
                                       KafkaConsumer<String, byte[]> consumer,
                                       JdbcCommandDedupeRepository dedupe,
                                       WorkflowScheduler workflowScheduler,
                                       ObjectMapper mapper,
                                       MasterMetrics metrics) {
    this.props = Objects.requireNonNull(props);
    this.consumer = Objects.requireNonNull(consumer);
    this.dedupe = Objects.requireNonNull(dedupe);
    this.workflowScheduler = Objects.requireNonNull(workflowScheduler);
    this.mapper = Objects.requireNonNull(mapper);
    this.metrics = Objects.requireNonNull(metrics);
  }

  @Override
  public void start() {
    if (running) return;
    running = true;
    consumer.subscribe(List.of(props.getKafka().getCommandsTopic()));
    loop.execute(this::runLoop);
    log.info("KafkaMasterCommandConsumerLoop started topic={} groupId={}", props.getKafka().getCommandsTopic(), props.getKafka().getMasterGroupId());
  }

  @Override
  public void stop() {
    running = false;
    try { consumer.wakeup(); } catch (Exception ignored) {}
    loop.shutdownNow();
    try { consumer.close(); } catch (Exception ignored) {}
    log.info("KafkaMasterCommandConsumerLoop stopped");
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

          metrics.commandKafkaConsumed.add(1);

          // Dedupe at master boundary for at-least-once Kafka delivery
          boolean first = dedupe.tryMarkProcessing(cmd.commandId());
          if (!first) {
            continue;
          }

          try {
            long workflowInstanceId = workflowScheduler.createAndSchedule(
                cmd.tenantId(),
                cmd.workflowCode(),
                cmd.workflowVersion(),
                cmd.payloadJson()
            );
            metrics.workflowScheduled.add(1);
            dedupe.markOutcome(cmd.commandId(), "DONE", "workflowInstanceId=" + workflowInstanceId);
          } catch (Exception ex) {
            metrics.commandKafkaError.add(1);
            dedupe.markOutcome(cmd.commandId(), "FAILED", ex.getMessage());
            log.warn("Master command {} failed: {}", cmd.commandId(), ex.toString());
          }
        }
        consumer.commitSync();
      } catch (org.apache.kafka.common.errors.WakeupException we) {
        // shutdown
      } catch (Exception e) {
        metrics.commandKafkaError.add(1);
        log.warn("KafkaMasterCommandConsumerLoop error: {}", e.toString());
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
      }
    }
  }
}
