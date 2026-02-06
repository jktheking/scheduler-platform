package com.acme.scheduler.master.config;

import com.acme.scheduler.master.adapter.jdbc.*;
import com.acme.scheduler.master.kafka.*;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.port.WorkflowScheduler;
import com.acme.scheduler.master.runtime.*;
import com.acme.scheduler.master.trigger.QuartzTriggerEngine;
import com.acme.scheduler.master.trigger.TimeWheelTriggerEngine;
import com.acme.scheduler.master.trigger.TriggerEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Qualifier;
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
    return new ObjectMapper().findAndRegisterModules();
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
                                     DagRuntime dagRuntime,
                                     MasterMetrics metrics) {
    var t = props.getTrigger();
    if (t.getEngine() == MasterKafkaProperties.Trigger.Engine.QUARTZ) {
      return new QuartzTriggerEngine(repo, dagRuntime, metrics, t.getQuartzPollMs(), t.getDrainBatch());
    }
    return new TimeWheelTriggerEngine(repo, dagRuntime, metrics, t.getWheelShards(), t.getWheelTickMs(), t.getWheelSlots(), t.getWheelLookaheadMs(), t.getDrainBatch());
  }

  // ---------- DAG runtime wiring

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public JdbcDagTaskInstanceRepository jdbcDagTaskInstanceRepository(JdbcTemplate jdbc) {
    return new JdbcDagTaskInstanceRepository(jdbc);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public JdbcWorkflowDefinitionRepository jdbcWorkflowDefinitionRepository(JdbcTemplate jdbc) {
    return new JdbcWorkflowDefinitionRepository(jdbc);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public JdbcDagEdgeRepository jdbcDagEdgeRepository(JdbcTemplate jdbc) {
    return new JdbcDagEdgeRepository(jdbc);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public JdbcTemplateWorkflowInstanceStatus jdbcTemplateWorkflowInstanceStatus(JdbcTemplate jdbc) {
    return new JdbcTemplateWorkflowInstanceStatus(jdbc);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public WorkflowMaterializer workflowMaterializer(JdbcWorkflowDefinitionRepository defs,
                                                   JdbcDagTaskInstanceRepository tasks,
                                                   ObjectMapper mapper) {
    return new WorkflowMaterializer(defs, tasks, mapper);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public DagProgressionEngine dagProgressionEngine(JdbcDagEdgeRepository edges,
                                                   JdbcDagTaskInstanceRepository tasks,
                                                   JdbcTemplateWorkflowInstanceStatus wi,
                                                   KafkaReadyPublisher publisher,
                                                   ObjectMapper mapper,
                                                   MasterMetrics metrics) {
    return new DagProgressionEngine(edges, tasks, wi, publisher, mapper, metrics);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public RetryPlanner retryPlanner(JdbcDagTaskInstanceRepository tasks,
                                   JdbcTriggerRepository triggers,
                                   MasterMetrics metrics) {
    return new RetryPlanner(tasks, triggers, metrics);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public DagRuntime dagRuntime(WorkflowMaterializer materializer,
                               JdbcDagTaskInstanceRepository tasks,
                               JdbcTemplateWorkflowInstanceStatus wi,
                               KafkaReadyPublisher publisher,
                               DagProgressionEngine progression,
                               RetryPlanner retryPlanner,
                               ObjectMapper mapper,
                               MasterMetrics metrics) {
    return new DefaultDagRuntime(materializer, tasks, wi, publisher, progression, retryPlanner, mapper, metrics);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public KafkaConsumer<String, byte[]> taskStateConsumer(MasterKafkaProperties props) {
    return new KafkaConsumer<>(KafkaClientFactory.consumerProps(props, props.getKafka().getMasterGroupId() + "-taskstate"));
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public KafkaTaskStateConsumerLoop kafkaTaskStateConsumerLoop(MasterKafkaProperties props,
                                                             KafkaConsumer<String, byte[]> taskStateConsumer,
                                                             DagRuntime dagRuntime,
                                                             ObjectMapper mapper,
                                                             MasterMetrics metrics) {
    return new KafkaTaskStateConsumerLoop(props, taskStateConsumer, dagRuntime, mapper, metrics);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public SmartLifecycle taskStateConsumerLifecycle(KafkaTaskStateConsumerLoop loop) {
    return new SmartLifecycle() {
      private volatile boolean running = false;
      @Override public void start() { loop.start(); running = true; }
      @Override public void stop() { loop.stop(); running = false; }
      @Override public boolean isRunning() { return running; }
      @Override public int getPhase() { return 0; }
    };
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public TaskEnqueueReconciler taskEnqueueReconciler(JdbcTemplate jdbc,
                                                     KafkaReadyPublisher publisher,
                                                     ObjectMapper mapper,
                                                     MasterMetrics metrics) {
    return new TaskEnqueueReconciler(jdbc, publisher, mapper, metrics, 30_000);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER", matchIfMissing = true)
  public SmartLifecycle taskEnqueueReconcilerLifecycle(TaskEnqueueReconciler r) {
    return new SmartLifecycle() {
      private volatile boolean running = false;
      @Override public void start() { r.start(); running = true; }
      @Override public void stop() { r.stop(); running = false; }
      @Override public boolean isRunning() { return running; }
      @Override public int getPhase() { return 0; }
    };
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
		                                               @Qualifier("writerConsumer") 
                                                       KafkaConsumer<String, byte[]> writerConsumer,
                                                       @Qualifier("jdbcCommandWriter")
                                                       JdbcCommandWriter writer,
                                                       ObjectMapper mapper,
                                                       MasterMetrics metrics) {
    return new KafkaCommandWriterLoop(props, writerConsumer, writer, mapper, metrics);
  }
  
  


  // ---------- MASTER role: Kafka -> plan/instance/trigger materialization
  
  
  //raw-command materialization in database;  Reads scheduler.commands.v1 and persists to Postgres t_command with ON CONFLICT DO NOTHING.
  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER")
  public KafkaConsumer<String, byte[]> masterWriterConsumer(MasterKafkaProperties props) {
    return new KafkaConsumer<>(KafkaClientFactory.consumerProps(props, props.getKafka().getWriterGroupId()));
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER")
  public JdbcCommandWriter masterJdbcCommandWriter(JdbcTemplate jdbc) {
    return new JdbcCommandWriter(jdbc);
  }

  
  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "role", havingValue = "MASTER")
  public KafkaCommandWriterLoop masterKafkaCommandWriterLoop(MasterKafkaProperties props,
		                                               @Qualifier("masterWriterConsumer")
                                                       KafkaConsumer<String, byte[]> writerConsumer,
                                                       @Qualifier("masterJdbcCommandWriter")
                                                       JdbcCommandWriter writer,
                                                       ObjectMapper mapper,
                                                       MasterMetrics metrics) {
    return new KafkaCommandWriterLoop(props, writerConsumer, writer, mapper, metrics);
  }
  

  // Reads scheduler.commands.v1 and materializes workflow instances/plans/triggers in DB.
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
		                                                               @Qualifier("masterConsumer")
                                                                       KafkaConsumer<String, byte[]> masterConsumer,
                                                                       JdbcCommandDedupeRepository dedupe,
                                                                       WorkflowScheduler workflowScheduler,
                                                                       ObjectMapper mapper,
                                                                       MasterMetrics metrics) {
    return new KafkaMasterCommandConsumerLoop(props, masterConsumer, dedupe, workflowScheduler, mapper, metrics);
  }
}
