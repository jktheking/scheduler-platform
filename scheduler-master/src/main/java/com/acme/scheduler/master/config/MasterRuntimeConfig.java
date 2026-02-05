package com.acme.scheduler.master.config;

import com.acme.scheduler.master.adapter.jdbc.JdbcCommandQueue;
import com.acme.scheduler.master.adapter.jdbc.JdbcWorkflowScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.port.CommandQueue;
import com.acme.scheduler.master.port.WorkflowScheduler;
import com.acme.scheduler.master.runtime.MasterCommandProcessor;
import com.acme.scheduler.master.runtime.MasterSchedulerLoop;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class MasterRuntimeConfig {

  @Bean
  public TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
    return new TransactionTemplate(tm);
  }

  @Bean
  public WorkflowScheduler workflowScheduler(JdbcTemplate jdbc, ObjectMapper mapper) {
    return new JdbcWorkflowScheduler(jdbc, mapper);
  }

  /**
   * Optional legacy driver: DB polling (CommandQueue) -> MasterSchedulerLoop.
   * Default  is Kafka-driven. Enable only if:
   *   scheduler.master.command-driver=DBPOLL
   */
  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "command-driver", havingValue = "DBPOLL")
  public CommandQueue commandQueue(JdbcTemplate jdbc, TransactionTemplate tx) {
    return new JdbcCommandQueue(jdbc, tx);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "command-driver", havingValue = "DBPOLL")
  public MasterCommandProcessor masterCommandProcessor(CommandQueue queue, WorkflowScheduler scheduler, MasterMetrics metrics) {
    return new MasterCommandProcessor(queue, scheduler, metrics);
  }

  @Bean
  @ConditionalOnProperty(prefix = "scheduler.master", name = "command-driver", havingValue = "DBPOLL")
  public MasterSchedulerLoop masterSchedulerLoop(CommandQueue queue, MasterCommandProcessor processor, MasterMetrics metrics) {
    return new MasterSchedulerLoop(queue, processor, metrics);
  }
}
