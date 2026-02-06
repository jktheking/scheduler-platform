package com.acme.scheduler.api.dto.schedule;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Create a schedule for a workflow definition (durable trigger persisted in Postgres)")
public record CreateScheduleRequest(
 @NotNull @Schema(example = "123456789") Long projectCode,
 @NotNull @Schema(example = "900001") Long processDefinitionCode,
 @NotBlank @Schema(description = "cron expression", example = "0 0 2 * * ?") String crontab,
 @Schema(description = "timezone id", example = "Asia/Kolkata") String timezoneId,
 @Schema(description = "schedule start time ISO-8601", example = "2026-02-01T00:00:00Z") String startTime,
 @Schema(description = "schedule end time ISO-8601", example = "2027-02-01T00:00:00Z") String endTime,
 @Schema(description = "scheduler failureStrategy", example = "END") String failureStrategy,
 @Schema(description = "scheduler warningType", example = "NONE") String warningType,
 @Schema(description = "scheduler warningGroupId", example = "0") Long warningGroupId,
 @Schema(description = "scheduler workerGroup", example = "default") String workerGroup,
 @Schema(description = "scheduler environmentCode", example = "0") Long environmentCode
) {}
