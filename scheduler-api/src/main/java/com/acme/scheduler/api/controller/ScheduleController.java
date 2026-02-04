package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.api.dto.PageResponse;
import com.acme.scheduler.api.dto.schedule.CreateScheduleRequest;
import com.acme.scheduler.api.dto.schedule.ScheduleDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/scheduler/projects/{projectCode}/schedules")
@Tag(name = "Scheduling APIs")
public class ScheduleController {

 @PostMapping("/create")
 public ApiResponse<ScheduleDto> create(@PathVariable long projectCode,
 @Valid @RequestBody CreateScheduleRequest req) {
 var dto = new ScheduleDto(70001L, req.processDefinitionCode(), req.crontab(), "OFFLINE", Instant.now(), Instant.now());
 return ApiResponse.ok(dto);
 }

 @PostMapping("/online")
 public ApiResponse<Void> online(@PathVariable long projectCode, @RequestParam long scheduleId) {
 return ApiResponse.ok(null);
 }

 @PostMapping("/offline")
 public ApiResponse<Void> offline(@PathVariable long projectCode, @RequestParam long scheduleId) {
 return ApiResponse.ok(null);
 }

 @GetMapping("/list")
 public ApiResponse<PageResponse<ScheduleDto>> list(@PathVariable long projectCode,
 @RequestParam(defaultValue = "1") int pageNo,
 @RequestParam(defaultValue = "10") int pageSize) {
 var items = List.of(new ScheduleDto(70001L, 900001L, "0 0 2 * * ?", "OFFLINE", Instant.now(), Instant.now()));
 return ApiResponse.ok(new PageResponse<>(1, pageNo, pageSize, items));
 }
}
