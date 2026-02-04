package com.acme.scheduler.api.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Create or update workflow definition (process definition)")
public record CreateWorkflowDefinitionRequest(
 @NotNull @Schema(example = "123456789") Long projectCode,
 @NotBlank @Schema(example = "daily_etl") String name,
 @Schema(example = "daily etl pipeline") String description,
 @Schema(description = "workflow editor locations JSON", example = "{\"taskCode\":{\"x\":100,\"y\":200}}") String locations,
 @Schema(description = "global parameters JSON array", example = "[{\"prop\":\"bizDate\",\"value\":\"${system.biz.date}\"}]") String globalParams,
 @NotBlank @Schema(description = "task definitions JSON array", example = "[{\"code\":111,\"name\":\"t1\",\"type\":\"SHELL\",\"params\":\"{...}\"}]") String taskDefinitionJson,
 @NotBlank @Schema(description = "task relations JSON array", example = "[{\"preTaskCode\":0,\"postTaskCode\":111}]") String taskRelationJson,
 @Schema(description = "tenant code/name", example = "default") String tenantCode
) {}
