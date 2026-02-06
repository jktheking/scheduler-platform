package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.service.query.GetWorkflowInstanceUseCase;
import com.acme.scheduler.service.query.GetWorkflowTrackingUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflow-instances")
@Tag(name = "Workflow Instances", description = "Query workflow instance state, task instances, and tracking checkpoints.")
public class WorkflowInstanceController {

	private final GetWorkflowInstanceUseCase useCase;
	private final GetWorkflowTrackingUseCase trackingUseCase;

	public WorkflowInstanceController(GetWorkflowInstanceUseCase useCase, GetWorkflowTrackingUseCase trackingUseCase) {
		this.useCase = useCase;
		this.trackingUseCase = trackingUseCase;
	}

	@Operation(summary = "Get a workflow instance", description = "Returns the workflow instance summary (status, timestamps) and associated metadata.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Instance found."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found.") })
	@GetMapping("/{id}")
	public ApiResponse<GetWorkflowInstanceUseCase.Response> get(@PathVariable long id) {
		return ApiResponse.ok(useCase.handle(id));
	}

	@Operation(summary = "List task instances for a workflow instance", description = "Returns all task instances (one row per DAG task execution attempt) for the given workflow instance.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Task list.") })
	@GetMapping("/{id}/tasks")
	public ApiResponse<?> tasks(@PathVariable long id) {
		return ApiResponse.ok(useCase.handle(id).tasks());
	}

	@Operation(summary = "Get tracking checkpoints", description = "Aggregated view of where the workflow is right now: instance state, latest command state, trigger checkpoints, and task state summary. Includes persisted failure reasons (last_error fields) when present.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Tracking view.") })
	@GetMapping("/{id}/tracking")
	public ApiResponse<?> tracking(@PathVariable long id) {
		return ApiResponse.ok(trackingUseCase.handle(id));
	}
}
