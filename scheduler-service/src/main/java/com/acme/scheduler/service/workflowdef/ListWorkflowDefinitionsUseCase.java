package com.acme.scheduler.service.workflowdef;

import com.acme.scheduler.service.port.WorkflowDefinitionGateway;

import java.util.List;
import java.util.Objects;

/** Lists latest workflow definitions for a tenant. */
public final class ListWorkflowDefinitionsUseCase {

  private final WorkflowDefinitionGateway gw;

  public ListWorkflowDefinitionsUseCase(WorkflowDefinitionGateway gw) {
    this.gw = Objects.requireNonNull(gw);
  }

  public List<WorkflowDefinitionGateway.WorkflowSummary> handle(String tenantId, int limit) {
    return gw.listLatest(tenantId == null ? "default" : tenantId, Math.max(1, limit));
  }
}
