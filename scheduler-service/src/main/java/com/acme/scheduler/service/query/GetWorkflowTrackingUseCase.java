package com.acme.scheduler.service.query;

import com.acme.scheduler.service.port.WorkflowTrackingQueryGateway;

import java.util.Objects;

public final class GetWorkflowTrackingUseCase {

  private final WorkflowTrackingQueryGateway gw;

  public GetWorkflowTrackingUseCase(WorkflowTrackingQueryGateway gw) {
    this.gw = Objects.requireNonNull(gw);
  }

  public WorkflowTrackingQueryGateway.TrackingView handle(long workflowInstanceId) {
    return gw.getTracking(workflowInstanceId)
        .orElseThrow(() -> new IllegalArgumentException("instance not found: " + workflowInstanceId));
  }
}
