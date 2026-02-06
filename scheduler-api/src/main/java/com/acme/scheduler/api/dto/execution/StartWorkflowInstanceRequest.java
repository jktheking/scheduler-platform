package com.acme.scheduler.api.dto.execution;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Start a workflow instance (ingestion request appended to the WAL; master processes asynchronously)")
public record StartWorkflowInstanceRequest(
 @NotNull @Schema(example = "900001") Long processDefinitionCode,
 @Schema(description = "ISO-8601 schedule time (optional)") String scheduleTime,
 @Schema(description = "scheduler execType/ commandType", example = "START_PROCESS") String execType,
 @Schema(description = "scheduler failureStrategy", example = "END") String failureStrategy,
 @Schema(description = "scheduler warningType", example = "NONE") String warningType,
 @Schema(description = "scheduler warningGroupId", example = "0") Long warningGroupId,
 @Schema(description = "scheduler workerGroup", example = "default") String workerGroup,
 @Schema(description = "scheduler environmentCode", example = "0") Long environmentCode,
 @Schema(description = "scheduler runMode", example = "RUN_PARALLEL") String runMode,
 @Schema(description = "scheduler startNodeList/ startNodes (implementation-defined). Keep as JSON array string.", example = "[111,222]") String startNodeList,
 @Schema(description = "scheduler taskDependType (placeholder; filled later)", example = "TASK_ONLY") String taskDependType,
 @NotBlank @Schema(description = "Idempotency key for start request", example = "req-01HXYZ...") String idempotencyKey
) {}
