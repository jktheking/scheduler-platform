package com.acme.scheduler.api.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Workflow definition summary")
public record WorkflowDefinitionDto(
 @Schema(example = "900001") long code,
 @Schema(example = "1") int version,
 @Schema(example = "daily_etl") String name,
 String description,
 @Schema(example = "ONLINE") String releaseState,
 Instant createTime,
 Instant updateTime
) {}
