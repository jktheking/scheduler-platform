package com.acme.scheduler.api.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User summary")
public record UserDto(
 @Schema(example = "100") long id,
 @Schema(example = "jk") String userName,
 @Schema(example = "ADMIN") String userType,
 @Schema(example = "default") String tenantCode
) {}
