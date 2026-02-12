package com.acme.scheduler.common.alert;

import java.time.Instant;

/**
 * Framework-neutral alert event contract.
 *
 * <p>Published by scheduler-master to Kafka topic {@code scheduler.alerts.v1}, consumed by
 * scheduler-alert-server for dispatching to external systems.
 */
public record AlertEvent(
    String eventId,
    AlertType type,
    AlertSeverity severity,
    String tenantId,
    long workflowInstanceId,
    long workflowCode,
    int workflowVersion,
    Long taskInstanceId,
    Long taskCode,
    Integer attempt,
    Long triggerId,
    Instant occurredAt,
    String message,
    String details,
    String traceParent
) {}
