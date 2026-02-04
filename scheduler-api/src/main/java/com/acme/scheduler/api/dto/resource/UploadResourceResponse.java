package com.acme.scheduler.api.dto.resource;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resource upload response")
public record UploadResourceResponse(
 @Schema(example = "/resources/tenantA/scripts/job.sh") String fullName,
 @Schema(example = "FILE") String resourceType,
 @Schema(example = "SUCCESS") String status
) {}
