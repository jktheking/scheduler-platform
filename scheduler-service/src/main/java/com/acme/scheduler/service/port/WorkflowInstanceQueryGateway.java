package com.acme.scheduler.service.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Clean-arch port: query runtime instances. */
public interface WorkflowInstanceQueryGateway {

  Optional<WorkflowInstance> getInstance(long workflowInstanceId);

  List<TaskInstance> listTasks(long workflowInstanceId);

  record WorkflowInstance(long workflowInstanceId,
                          String tenantId,
                          long workflowCode,
                          int workflowVersion,
                          String status,
                          Instant createdAt,
                          Instant updatedAt) {}

  record TaskInstance(long taskInstanceId,
                      long workflowInstanceId,
                      long taskCode,
                      int taskVersion,
                      String taskName,
                      String taskType,
                      String status,
                      int attempt,
                      int maxAttempts,
                      Instant nextRunTime,
                      String workerId,
                      Instant claimedAt,
                      Instant startedAt,
                      Instant finishedAt,
                      String lastError) {}
}
