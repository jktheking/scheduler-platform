package com.acme.scheduler.master.runtime;

import java.time.Instant;

/**
 * Message published to Kafka (scheduler.tasks.ready.v1).
 * In later phases, this becomes a bucket-pointer or per-task message depending on fanout strategy.
 */
public record TaskReadyEvent(
    long workflowInstanceId,
    long triggerId,
    Instant dueTime,
    String payloadJson
) {}
