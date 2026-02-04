package com.acme.scheduler.service.command;

import com.acme.scheduler.domain.execution.CommandType;
import com.acme.scheduler.domain.ids.*;

import java.time.Instant;

public record Command(
    long commandId,
    CommandType type,
    WorkflowCode workflowCode,
    int workflowVersion,
    String payloadJson,
    IdempotencyKey idempotencyKey,
    Instant createdAt
) {}
