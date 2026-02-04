package com.acme.scheduler.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Scheduler-style API envelope")
public record ApiResponse<T>(
 @Schema(description = "0 means success") int code,
 @Schema(description = "human readable message") String msg,
 @Schema(description = "payload") T data,
 @Schema(description = "true when request is successful") boolean success,
 @Schema(description = "true when request failed") boolean failed
) {
 public static <T> ApiResponse<T> ok(T data) {
 return new ApiResponse<>(0, "success", data, true, false);
 }

 public static <T> ApiResponse<T> error(int code, String msg) {
 return new ApiResponse<>(code, msg, null, false, true);
 }
}
