package com.acme.scheduler.service.query;

import com.acme.scheduler.service.port.WorkflowInstanceQueryGateway;

import java.util.List;
import java.util.Objects;

public final class GetWorkflowInstanceUseCase {

  private final WorkflowInstanceQueryGateway gw;

  public GetWorkflowInstanceUseCase(WorkflowInstanceQueryGateway gw) {
    this.gw = Objects.requireNonNull(gw);
  }

  public Response handle(long workflowInstanceId) {
    var inst = gw.getInstance(workflowInstanceId).orElseThrow(() -> new IllegalArgumentException("instance not found: " + workflowInstanceId));
    List<WorkflowInstanceQueryGateway.TaskInstance> tasks = gw.listTasks(workflowInstanceId);
    return new Response(inst, tasks);
  }

  public record Response(WorkflowInstanceQueryGateway.WorkflowInstance instance,
                         List<WorkflowInstanceQueryGateway.TaskInstance> tasks) {}
}
