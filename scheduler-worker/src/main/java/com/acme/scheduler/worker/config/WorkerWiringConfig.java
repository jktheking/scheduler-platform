package com.acme.scheduler.worker.config;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.acme.scheduler.meter.OtelSchedulerMeter;
import com.acme.scheduler.meter.SchedulerMeter;
import com.acme.scheduler.worker.adapter.jdbc.JdbcTaskInstanceRepository;
import com.acme.scheduler.worker.exec.HttpTaskExecutor;
import com.acme.scheduler.worker.exec.ScriptTaskExecutor;
import com.acme.scheduler.worker.kafka.KafkaReadyTaskConsumerLoop;
import com.acme.scheduler.worker.kafka.KafkaTaskStatePublisher;
import com.acme.scheduler.worker.kafka.WorkerKafkaClientFactory;
import com.acme.scheduler.worker.kafka.WorkerKafkaProperties;
import com.acme.scheduler.worker.runtime.WorkerTaskOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;

@Configuration
public class WorkerWiringConfig {

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }

  @Bean
  public SchedulerMeter schedulerMeter() {
    Meter meter = GlobalOpenTelemetry.get().getMeter("scheduler-worker");
    return new OtelSchedulerMeter(meter);
  }

  @Bean
  public KafkaConsumer<String, byte[]> readyConsumer(WorkerKafkaProperties props) {
    return new KafkaConsumer<>(WorkerKafkaClientFactory.consumerProps(props));
  }

  @Bean
  public KafkaProducer<String, byte[]> stateProducer(WorkerKafkaProperties props) {
    return new KafkaProducer<>(WorkerKafkaClientFactory.producerProps(props));
  }

  @Bean
  public JdbcTaskInstanceRepository jdbcTaskInstanceRepository(JdbcTemplate jdbc) {
    return new JdbcTaskInstanceRepository(jdbc);
  }

  @Bean
  public HttpTaskExecutor httpTaskExecutor(ObjectMapper mapper) {
    return new HttpTaskExecutor(mapper);
  }

  @Bean
  public ScriptTaskExecutor scriptTaskExecutor(ObjectMapper mapper) {
    return new ScriptTaskExecutor(mapper);
  }

  @Bean
  public KafkaTaskStatePublisher kafkaTaskStatePublisher(KafkaProducer<String, byte[]> stateProducer,
                                                         WorkerKafkaProperties props,
                                                         ObjectMapper mapper,
                                                         SchedulerMeter meter) {
    return new KafkaTaskStatePublisher(stateProducer, props, mapper, meter);
  }

  @Bean
  public WorkerTaskOrchestrator workerTaskOrchestrator(JdbcTaskInstanceRepository repo,
                                                       KafkaTaskStatePublisher publisher,
                                                       HttpTaskExecutor http,
                                                       ScriptTaskExecutor script,
                                                       SchedulerMeter meter) {
    return new WorkerTaskOrchestrator(repo, publisher, http, script, meter);
  }

  @Bean
  public KafkaReadyTaskConsumerLoop kafkaReadyTaskConsumerLoop(WorkerKafkaProperties props,
                                                               KafkaConsumer<String, byte[]> readyConsumer,
                                                               ObjectMapper mapper,
                                                               WorkerTaskOrchestrator orchestrator,
                                                               SchedulerMeter meter) {
    return new KafkaReadyTaskConsumerLoop(props, readyConsumer, mapper, orchestrator, meter);
  }

  @Bean
  public SmartLifecycle readyConsumerLifecycle(KafkaReadyTaskConsumerLoop loop) {
    return new SmartLifecycle() {
      private volatile boolean running = false;

      @Override public void start() { loop.start(); running = true; }
      @Override public void stop() { loop.stop(); running = false; }
      @Override public boolean isRunning() { return running; }
      @Override public int getPhase() { return 0; }
    };
  }
}