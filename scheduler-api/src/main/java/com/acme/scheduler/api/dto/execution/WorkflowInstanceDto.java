package com.acme.scheduler.api.dto.execution;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Workflow instance summary")
public record WorkflowInstanceDto(
 @Schema(example = "10001") long id,
 @Schema(example = "900001") long processDefinitionCode,
 @Schema(example = "1") int processDefinitionVersion,
 @Schema(example = "RUNNING") String state,
 Instant startTime,
 Instant endTime
) {}
