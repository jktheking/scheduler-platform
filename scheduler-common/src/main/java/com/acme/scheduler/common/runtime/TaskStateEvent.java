package com.acme.scheduler.common.runtime;

import java.time.Instant;

/**
 * Worker -> master state transition events.
 */
public record TaskStateEvent(
    long workflowInstanceId,
    long taskInstanceId,
    int attempt,
    String state, // STARTED|SUCCEEDED|FAILED|RETRY_SCHEDULED|DLQ|SKIPPED
    Instant startedAt,
    Instant finishedAt,
    Integer httpStatus,
    Integer exitCode,
    String errorMessage,
    String traceParent
) {}
