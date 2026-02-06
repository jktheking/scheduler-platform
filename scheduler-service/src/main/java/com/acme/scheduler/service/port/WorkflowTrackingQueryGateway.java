package com.acme.scheduler.service.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-only "where is it now" tracking view for a workflow instance.
 *
 * This is intentionally query-only and does not change runtime semantics.
 */
public interface WorkflowTrackingQueryGateway {

  Optional<TrackingView> getTracking(long workflowInstanceId);

  record TrackingView(
      WorkflowInstanceQueryGateway.WorkflowInstance instance,
      CommandInfo latestCommand,
      List<TriggerInfo> triggers,
      TaskSummary taskSummary
  ) {}

  record CommandInfo(
      String commandId,
      String commandType,
      Instant createdAt,
      String status,
      String lastError
  ) {}

  record TriggerInfo(
      long triggerId,
      Instant dueTime,
      String status,
      String claimedBy,
      Instant claimedAt,
      Instant updatedAt,
      String lastError
  ) {}

  record TaskSummary(
      long total,
      long queued,
      long claimed,
      long running,
      long success,
      long failed,
      long skipped,
      Instant oldestQueuedNextRunTime
  ) {}
}
