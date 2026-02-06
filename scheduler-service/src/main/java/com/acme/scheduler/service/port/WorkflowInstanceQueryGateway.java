package com.acme.scheduler.service.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Clean-arch port: query runtime instances. */
public interface WorkflowInstanceQueryGateway {

  Optional<WorkflowInstance> getInstance(long workflowInstanceId);

  List<TaskInstance> listTasks(long workflowInstanceId);

  /** Recent triggers for the instance (most recent first). */
  List<TriggerRow> listRecentTriggers(long workflowInstanceId, int limit);

  /** Latest command for this workflow definition (by created_at desc). */
  Optional<CommandRow> getLatestCommand(String tenantId, long workflowCode, int workflowVersion);

  record WorkflowInstance(long workflowInstanceId,
                          String tenantId,
                          long workflowCode,
                          int workflowVersion,
                          String status,
                          Instant createdAt,
                          Instant updatedAt,
                          Instant scheduleTime,
                          String lastError) {}

  record TriggerRow(long triggerId,
                    long workflowInstanceId,
                    Instant dueTime,
                    String status,
                    String claimedBy,
                    Instant claimedAt,
                    Instant updatedAt,
                    String lastError) {}

  record CommandRow(String commandId,
                    String tenantId,
                    String idempotencyKey,
                    String commandType,
                    long workflowCode,
                    int workflowVersion,
                    Instant createdAt,
                    String status,
                    String lastErrorMessage) {}

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
