package com.acme.scheduler.master.runtime;

import com.acme.scheduler.domain.execution.CommandType;

import java.time.Instant;

public record CommandRecord(
    String commandId,
    String tenantId,
    CommandType type,
    long workflowCode,
    int workflowVersion,
    String payloadJson,
    String idempotencyKey,
    Instant createdAt
) {}
