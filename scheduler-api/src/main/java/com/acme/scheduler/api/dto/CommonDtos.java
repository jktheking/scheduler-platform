package com.acme.scheduler.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public final class CommonDtos {
 private CommonDtos() {}

 @Schema(description = "Simple key/value param representation; aligns with DS globalParams/localParams JSON semantics")
 public record Param(
 @Schema(example = "bizDate") String prop,
 @Schema(example = "${system.biz.date}") String value
 ) {}

 @Schema(description = "Standard audit fields")
 public record Audit(
 Instant createTime,
 Instant updateTime,
 long createUserId,
 long updateUserId
 ) {}
}
