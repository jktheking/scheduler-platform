package com.acme.scheduler.domain.instance;

import com.acme.scheduler.domain.DomainValidationException;
import com.acme.scheduler.domain.execution.CommandType;
import com.acme.scheduler.domain.execution.FailureStrategy;
import com.acme.scheduler.domain.execution.RunMode;
import com.acme.scheduler.domain.execution.WarningType;
import com.acme.scheduler.domain.ids.WorkflowCode;
import com.acme.scheduler.domain.ids.WorkflowInstanceId;
import com.acme.scheduler.domain.infra.EnvironmentCode;
import com.acme.scheduler.domain.infra.WorkerGroup;
import com.acme.scheduler.domain.state.WorkflowExecutionStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Runtime execution of a WorkflowDefinition (bound to a specific version).
 */
public record WorkflowInstance(
 WorkflowInstanceId id,
 WorkflowCode workflowCode,
 int workflowVersion,
 WorkflowExecutionStatus state,
 CommandType commandType,
 Instant scheduleTime,
 Instant startTime,
 Instant endTime,
 FailureStrategy failureStrategy,
 WarningType warningType,
 long warningGroupId,
 RunMode runMode,
 WorkerGroup workerGroup,
 EnvironmentCode environmentCode
) {
 public WorkflowInstance {
 Objects.requireNonNull(id, "id");
 Objects.requireNonNull(workflowCode, "workflowCode");
 if (workflowVersion <= 0) throw new DomainValidationException("workflowVersion must be positive");
 state = state == null ? WorkflowExecutionStatus.SUBMITTED : state;
 commandType = commandType == null ? CommandType.START_PROCESS : commandType;
 failureStrategy = failureStrategy == null ? FailureStrategy.END : failureStrategy;
 warningType = warningType == null ? WarningType.NONE : warningType;
 if (warningGroupId < 0) throw new DomainValidationException("warningGroupId must be >= 0");
 runMode = runMode == null ? RunMode.RUN_PARALLEL : runMode;
 workerGroup = workerGroup == null ? new WorkerGroup("default") : workerGroup;
 environmentCode = environmentCode == null ? new EnvironmentCode(0) : environmentCode;
 }

 public WorkflowInstance withState(WorkflowExecutionStatus next) {
 if (!state.canTransitionTo(next)) {
 throw new DomainValidationException("Illegal workflow state transition: " + state + " -> " + next);
 }
 return new WorkflowInstance(
 id, workflowCode, workflowVersion, next, commandType, scheduleTime, startTime, endTime,
 failureStrategy, warningType, warningGroupId, runMode, workerGroup, environmentCode
 );
 }
}
