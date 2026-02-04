package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.api.dto.PageResponse;
import com.acme.scheduler.api.dto.workflow.CreateWorkflowDefinitionRequest;
import com.acme.scheduler.api.dto.workflow.WorkflowDefinitionDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/scheduler/projects/{projectCode}/process-definition")
@Tag(name = "Workflow Definition APIs")
public class WorkflowDefinitionController {

 @PostMapping("/create")
 public ApiResponse<WorkflowDefinitionDto> create(@PathVariable long projectCode,
 @Valid @RequestBody CreateWorkflowDefinitionRequest req) {
// Stub implementation: real service wiring comes in Step 2+
 WorkflowDefinitionDto dto = new WorkflowDefinitionDto(
 900001L, 1, req.name(), req.description(), "OFFLINE", Instant.now(), Instant.now()
 );
 return ApiResponse.ok(dto);
 }

 @GetMapping("/list")
 public ApiResponse<PageResponse<WorkflowDefinitionDto>> list(@PathVariable long projectCode,
 @RequestParam(defaultValue = "1") int pageNo,
 @RequestParam(defaultValue = "10") int pageSize,
 @RequestParam(required = false) String searchVal) {
 var items = List.of(new WorkflowDefinitionDto(900001L, 1, "daily_etl", "demo", "OFFLINE", Instant.now(), Instant.now()));
 return ApiResponse.ok(new PageResponse<>(1, pageNo, pageSize, items));
 }

 @PostMapping("/release")
 public ApiResponse<Void> release(@PathVariable long projectCode,
 @RequestParam long code,
 @RequestParam String releaseState) {
 return ApiResponse.ok(null);
 }
}
