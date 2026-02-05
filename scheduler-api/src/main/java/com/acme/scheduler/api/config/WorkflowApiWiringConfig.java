package com.acme.scheduler.api.config;

import com.acme.scheduler.adapter.jdbc.JdbcDlqGateway;
import com.acme.scheduler.adapter.jdbc.JdbcWorkflowDefinitionGateway;
import com.acme.scheduler.adapter.jdbc.JdbcWorkflowInstanceQueryGateway;
import com.acme.scheduler.service.dlq.ListDlqUseCase;
import com.acme.scheduler.service.dlq.ReplayDlqUseCase;
import com.acme.scheduler.service.query.GetWorkflowInstanceUseCase;
import com.acme.scheduler.service.workflowdef.ListWorkflowDefinitionsUseCase;
import com.acme.scheduler.service.workflowdef.UpsertWorkflowDefinitionUseCase;
import com.acme.scheduler.service.port.DlqGateway;
import com.acme.scheduler.service.port.WorkflowDefinitionGateway;
import com.acme.scheduler.service.port.WorkflowInstanceQueryGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class WorkflowApiWiringConfig {

  @Bean
  public WorkflowDefinitionGateway workflowDefinitionGateway(JdbcTemplate jdbc) {
    return new JdbcWorkflowDefinitionGateway(jdbc);
  }

  @Bean
  public WorkflowInstanceQueryGateway workflowInstanceQueryGateway(JdbcTemplate jdbc) {
    return new JdbcWorkflowInstanceQueryGateway(jdbc);
  }

  @Bean
  public DlqGateway dlqGateway(JdbcTemplate jdbc) {
    return new JdbcDlqGateway(jdbc);
  }

  @Bean
  public UpsertWorkflowDefinitionUseCase upsertWorkflowDefinitionUseCase(WorkflowDefinitionGateway gw) {
    return new UpsertWorkflowDefinitionUseCase(gw);
  }

  @Bean
  public ListWorkflowDefinitionsUseCase listWorkflowDefinitionsUseCase(WorkflowDefinitionGateway gw) {
    return new ListWorkflowDefinitionsUseCase(gw);
  }

  @Bean
  public GetWorkflowInstanceUseCase getWorkflowInstanceUseCase(WorkflowInstanceQueryGateway gw) {
    return new GetWorkflowInstanceUseCase(gw);
  }

  @Bean
  public ListDlqUseCase listDlqUseCase(DlqGateway gw) {
    return new ListDlqUseCase(gw);
  }

  @Bean
  public ReplayDlqUseCase replayDlqUseCase(DlqGateway gw) {
    return new ReplayDlqUseCase(gw);
  }
}
