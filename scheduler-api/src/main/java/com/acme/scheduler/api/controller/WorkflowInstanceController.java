package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.service.query.GetWorkflowInstanceUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/scheduler/instances")
@Tag(name = "Workflow Instance APIs")
public class WorkflowInstanceController {

  private final GetWorkflowInstanceUseCase useCase;

  public WorkflowInstanceController(GetWorkflowInstanceUseCase useCase) {
    this.useCase = useCase;
  }

  @GetMapping("/{id}")
  public ApiResponse<GetWorkflowInstanceUseCase.Response> get(@PathVariable long id) {
    return ApiResponse.ok(useCase.handle(id));
  }

  @GetMapping("/{id}/tasks")
  public ApiResponse<?> tasks(@PathVariable long id) {
    return ApiResponse.ok(useCase.handle(id).tasks());
  }
}
