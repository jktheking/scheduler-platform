package com.acme.scheduler.master.config;

import com.acme.scheduler.master.adapter.jdbc.JdbcCommandDedupeRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcCommandWriter;
import com.acme.scheduler.master.adapter.jdbc.JdbcTriggerRepository;
import com.acme.scheduler.master.kafka.*;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.port.WorkflowScheduler;
import com.acme.scheduler.master.trigger.QuartzTriggerEngine;
import com.acme.scheduler.master.trigger.TimeWheelTriggerEngine;
import com.acme.scheduler.master.trigger.TriggerEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class MasterKafkaWiringConfig {

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public KafkaProducer<String, byte[]> readyProducer(MasterKafkaProperties props) {
    return new KafkaProducer<>(KafkaClientFactory.producerProps(props));
  }

  @Bean
  public KafkaReadyPublisher kafkaReadyPublisher(KafkaProducer<String, byte[]> readyProducer,
                                                 MasterKafkaProperties props,
                                                 ObjectMapper mapper,
                                                 MasterMetrics metrics) {
    return new KafkaReadyPublisher(readyProducer, props, mapper, metrics);
  }

  @Bean
  public JdbcTriggerRepository jdbcTriggerRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
    return new JdbcTriggerRepository(jdbc, tx);
  }

  @Bean
  public TriggerEngine triggerEngine(MasterKafkaProperties props,
                                     JdbcTriggerRepository repo,
                                     KafkaReadyPublisher publisher,
                                     MasterMetrics metrics) {
    var t = props.getTrigger();
    if (t.getEngine() == MasterKafkaProperties.Trigger.Engine.QUARTZ) {
      return new QuartzTriggerEngine(repo, publisher, metrics, t.getQuartzPollMs(), t.getDrainBatch());
    }
    return new TimeWheelTriggerEngine(repo, publisher, metrics, t.getWheelShards(), t.getWheelTickMs(), t.getWheelSlots(), t.getWheelLookaheadMs(), t.getDrainBatch());
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public SmartLifecycle triggerEngineLifecycle(TriggerEngine engine) {
    return new SmartLifecycle() {
      private volatile boolean running = false;
      @Override public void start() { engine.start(); running = true; }
      @Override public void stop() { engine.stop(); running = false; }
      @Override public boolean isRunning() { return running; }
      @Override public int getPhase() { return 0; }
    };
  }

  // ---------- WRITER role: Kafka -> JDBC persistence

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "WRITER")
  public KafkaConsumer<String, byte[]> writerConsumer(MasterKafkaProperties props) {
    return new KafkaConsumer<>(KafkaClientFactory.consumerProps(props, props.getKafka().getWriterGroupId()));
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "WRITER")
  public JdbcCommandWriter jdbcCommandWriter(JdbcTemplate jdbc) {
    return new JdbcCommandWriter(jdbc);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "WRITER")
  public KafkaCommandWriterLoop kafkaCommandWriterLoop(MasterKafkaProperties props,
                                                       KafkaConsumer<String, byte[]> writerConsumer,
                                                       JdbcCommandWriter writer,
                                                       ObjectMapper mapper,
                                                       MasterMetrics metrics) {
    return new KafkaCommandWriterLoop(props, writerConsumer, writer, mapper, metrics);
  }

  // ---------- MASTER role: Kafka -> plan/instance/trigger materialization

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public KafkaConsumer<String, byte[]> masterConsumer(MasterKafkaProperties props) {
    return new KafkaConsumer<>(KafkaClientFactory.consumerProps(props, props.getKafka().getMasterGroupId()));
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public JdbcCommandDedupeRepository jdbcCommandDedupeRepository(JdbcTemplate jdbc) {
    return new JdbcCommandDedupeRepository(jdbc);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public KafkaMasterCommandConsumerLoop kafkaMasterCommandConsumerLoop(MasterKafkaProperties props,
                                                                       KafkaConsumer<String, byte[]> masterConsumer,
                                                                       JdbcCommandDedupeRepository dedupe,
                                                                       WorkflowScheduler workflowScheduler,
                                                                       ObjectMapper mapper,
                                                                       MasterMetrics metrics) {
    return new KafkaMasterCommandConsumerLoop(props, masterConsumer, dedupe, workflowScheduler, mapper, metrics);
  }
}
