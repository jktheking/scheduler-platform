package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.api.dto.PageResponse;
import com.acme.scheduler.api.dto.schedule.CreateScheduleRequest;
import com.acme.scheduler.api.dto.schedule.ScheduleDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectCode}/schedules")
@Tag(name = "Schedules", description = "Manage cron schedules that create workflow trigger events.")
public class ScheduleController {

 @Operation(summary="Create a schedule", description="Creates a schedule (cron expression) bound to a workflow definition code. Newly created schedules start in OFFLINE state until enabled.")
@ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Schedule created.")})
@PostMapping
 public ApiResponse<ScheduleDto> create(@PathVariable long projectCode,
 @Valid @RequestBody CreateScheduleRequest req) {
 var dto = new ScheduleDto(70001L, req.processDefinitionCode(), req.crontab(), "OFFLINE", Instant.now(), Instant.now());
 return ApiResponse.ok(dto);
 }

 @Operation(summary="Enable a schedule", description="Enables (puts ONLINE) an existing schedule so it can produce triggers.")
 @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Schedule enabled.")})
 @PostMapping("/{scheduleId}/enable")
 public ApiResponse<Void> enable(@PathVariable long projectCode, @PathVariable long scheduleId) {
   // Demo placeholder: wiring to persistence is not part of this repository stage.
   return ApiResponse.ok(null);
 }

 @Operation(summary="Disable a schedule", description="Disables (puts OFFLINE) an existing schedule so it stops producing triggers.")
 @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Schedule disabled.")})
 @PostMapping("/{scheduleId}/disable")
 public ApiResponse<Void> disable(@PathVariable long projectCode, @PathVariable long scheduleId) {
   // Demo placeholder: wiring to persistence is not part of this repository stage.
   return ApiResponse.ok(null);
 }

 @Operation(summary="List schedules", description="Returns paginated schedules for a project. (Demo placeholder implementation)")
@ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="Page of schedules.")})
@GetMapping
 public ApiResponse<PageResponse<ScheduleDto>> list(@PathVariable long projectCode,
 @RequestParam(defaultValue = "1") int pageNo,
 @RequestParam(defaultValue = "10") int pageSize) {
 var items = List.of(new ScheduleDto(70001L, 900001L, "0 0 2 * * ?", "OFFLINE", Instant.now(), Instant.now()));
 return ApiResponse.ok(new PageResponse<>(1, pageNo, pageSize, items));
 }
}
