package com.acme.scheduler.service.workflow;

import java.time.Instant;

public record CommandEnvelope(
    String tenantId,
    String commandId,
    String idempotencyKey,
    String commandType,       // START_PROCESS, SCHEDULED, COMPLEMENT_DATA...
    long workflowCode,
    int workflowVersion,
    Instant createdAt,
    String payloadJson
) {}
