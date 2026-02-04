package com.acme.scheduler.service.usecase;

import java.time.Instant;

import com.acme.scheduler.domain.execution.FailureStrategy;
import com.acme.scheduler.domain.execution.RunMode;
import com.acme.scheduler.domain.execution.WarningType;
import com.acme.scheduler.domain.ids.IdempotencyKey;
import com.acme.scheduler.domain.ids.WorkflowCode;
import com.acme.scheduler.domain.infra.EnvironmentCode;
import com.acme.scheduler.domain.infra.WorkerGroup;

public record StartWorkflowCommand(
    WorkflowCode workflowCode,
    int workflowVersion,
    Instant scheduleTime,
    FailureStrategy failureStrategy,
    WarningType warningType,
    long warningGroupId,
    RunMode runMode,
    WorkerGroup workerGroup,
    EnvironmentCode environmentCode,
    IdempotencyKey idempotencyKey
) {}
