package com.acme.scheduler.common.runtime;

import java.time.Instant;

/**
 * Payload embedded inside {@code com.acme.scheduler.master.runtime.TaskReadyEvent.payloadJson}.
 */
public record TaskDispatchEnvelope(
    long workflowInstanceId,
    long taskInstanceId,
    int attempt,
    String tenantId,
    String taskType,
    Instant dueTime,
    String payloadJson,
    String traceParent
) {}
