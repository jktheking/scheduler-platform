package com.acme.scheduler.api.dto.schedule;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Schedule summary")
public record ScheduleDto(
 @Schema(example = "70001") long id,
 @Schema(example = "900001") long processDefinitionCode,
 @Schema(example = "0 0 2 * * ?") String crontab,
 @Schema(example = "ONLINE") String state,
 Instant createTime,
 Instant updateTime
) {}
