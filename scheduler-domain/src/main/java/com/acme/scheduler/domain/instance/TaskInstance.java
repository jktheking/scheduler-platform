package com.acme.scheduler.domain.instance;

import com.acme.scheduler.domain.DomainValidationException;
import com.acme.scheduler.domain.ids.TaskCode;
import com.acme.scheduler.domain.ids.TaskInstanceId;
import com.acme.scheduler.domain.ids.WorkflowInstanceId;
import com.acme.scheduler.domain.state.TaskExecutionStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Runtime execution of a TaskDefinition (bound to a specific version) within a WorkflowInstance.
 */
public record TaskInstance(
 TaskInstanceId id,
 WorkflowInstanceId workflowInstanceId,
 TaskCode taskCode,
 int taskVersion,
 String taskType,
 TaskExecutionStatus state,
 int attempt,
 Instant startTime,
 Instant endTime
) {
 public TaskInstance {
 Objects.requireNonNull(id, "id");
 Objects.requireNonNull(workflowInstanceId, "workflowInstanceId");
 Objects.requireNonNull(taskCode, "taskCode");
 if (taskVersion <= 0) throw new DomainValidationException("taskVersion must be positive");
 if (taskType == null || taskType.isBlank()) throw new DomainValidationException("taskType must be non-empty");
 state = state == null ? TaskExecutionStatus.SUBMITTED_SUCCESS : state;
 if (attempt < 0) throw new DomainValidationException("attempt must be >= 0");
 }

 public TaskInstance withState(TaskExecutionStatus next) {
 if (!state.canTransitionTo(next)) {
 throw new DomainValidationException("Illegal task state transition: " + state + " -> " + next);
 }
 return new TaskInstance(id, workflowInstanceId, taskCode, taskVersion, taskType, next, attempt, startTime, endTime);
 }
}
