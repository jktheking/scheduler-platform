package com.acme.scheduler.service.port;

import java.util.List;

public interface WorkflowDefinitionGateway {

  record TaskSpec(long taskCode, int taskVersion, String name, String taskType, String definitionJson) {}

  record EdgeSpec(long fromTaskCode, long toTaskCode) {}

  record UpsertResult(long workflowCode, int workflowVersion) {}

  record WorkflowSummary(String tenantId, long workflowCode, int workflowVersion, String name) {}

  long allocateWorkflowCode();

  long allocateTaskCode();

  UpsertResult upsertWorkflowDefinition(
      String tenantId,
      Long workflowCodeOrNull,
      String name,
      List<TaskSpec> tasks,
      List<EdgeSpec> edges
  );

  List<WorkflowSummary> listLatest(String tenantId, int limit);
}
