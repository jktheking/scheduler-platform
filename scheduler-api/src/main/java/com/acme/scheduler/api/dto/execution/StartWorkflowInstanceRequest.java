package com.acme.scheduler.api.dto.execution;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Start workflow instance (DS:/executors/start-process-instance)")
public record StartWorkflowInstanceRequest(
 @NotNull @Schema(example = "900001") Long processDefinitionCode,
 @Schema(description = "ISO-8601 schedule time (optional). DS calls this scheduleTime in some docs.") String scheduleTime,
 @Schema(description = "DS execType/ commandType", example = "START_PROCESS") String execType,
 @Schema(description = "DS failureStrategy", example = "END") String failureStrategy,
 @Schema(description = "DS warningType", example = "NONE") String warningType,
 @Schema(description = "DS warningGroupId", example = "0") Long warningGroupId,
 @Schema(description = "DS workerGroup", example = "default") String workerGroup,
 @Schema(description = "DS environmentCode", example = "0") Long environmentCode,
 @Schema(description = "DS runMode", example = "RUN_PARALLEL") String runMode,
 @Schema(description = "DS startNodeList/ startNodes (implementation-defined). Keep as JSON array string.", example = "[111,222]") String startNodeList,
 @Schema(description = "DS taskDependType (placeholder; filled later)", example = "TASK_ONLY") String taskDependType,
 @NotBlank @Schema(description = "Idempotency key for start request", example = "req-01HXYZ...") String idempotencyKey
) {}
