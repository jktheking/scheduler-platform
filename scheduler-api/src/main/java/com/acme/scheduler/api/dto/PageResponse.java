package com.acme.scheduler.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Simple page response")
public record PageResponse<T>(
 @Schema(description = "total items") long total,
 @Schema(description = "page number (1-based)") int pageNo,
 @Schema(description = "page size") int pageSize,
 @Schema(description = "items") List<T> items
) {}
