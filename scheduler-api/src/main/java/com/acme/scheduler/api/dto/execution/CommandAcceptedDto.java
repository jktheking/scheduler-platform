package com.acme.scheduler.api.dto.execution;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Command accepted response (durable intent, instance creation happens later by master)")
public record CommandAcceptedDto(
    @Schema(example = "20001") long commandId,
    @Schema(example = "START_PROCESS") String commandType,
    @Schema(example = "QUEUED") String status,
    Instant acceptedAt
) {}
