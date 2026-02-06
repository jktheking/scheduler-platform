package com.acme.scheduler.api.controller;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.api.dto.PageResponse;
import com.acme.scheduler.api.dto.execution.StartWorkflowInstanceRequest;
import com.acme.scheduler.api.dto.execution.WorkflowInstanceDto;
import com.acme.scheduler.service.workflow.StartWorkflowUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * WorkflowExecutionController → StartWorkflowUseCase → IngestionGateway (JDBC |
 * Kafka WAL)
 *
 * This controller focuses on ingestion only; the master will later materialize
 * instances/DAG plans.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectCode}/workflow-instances")
@Tag(name = "Workflow Execution", description = "Submit workflow execution requests (ingestion). The master later materializes DAG and schedules tasks.")
public class WorkflowExecutionController {

	private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionController.class);

	private final StartWorkflowUseCase startWorkflowUseCase;
	private final ObjectMapper mapper;

	public WorkflowExecutionController(StartWorkflowUseCase startWorkflowUseCase, ObjectMapper mapper) {
		this.startWorkflowUseCase = startWorkflowUseCase;
		this.mapper = mapper;
	}

	@Operation(summary = "Submit a workflow instance for execution", description = "Ingests an execution request and appends a command to the ingestion WAL (Kafka or JDBC fallback).\n\nThis endpoint is intentionally asynchronous: it returns after the command is accepted. The master will later create the workflow instance, materialize the DAG, and enqueue tasks.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Accepted for ingestion (returns SUBMITTED placeholder).", content = @Content(schema = @Schema(implementation = com.acme.scheduler.api.dto.execution.WorkflowInstanceDto.class))),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rejected by admission control / backpressure."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error."),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Server error.") })
	@PostMapping
	public ApiResponse<WorkflowInstanceDto> start(@PathVariable long projectCode,
			@Valid @RequestBody StartWorkflowInstanceRequest req,
			@RequestHeader(value = "X-Tenant-Id", required = false) String tenantIdHeader,
			@RequestHeader(value = "traceparent", required = false) String traceParent) {
		// Tenant is carried out-of-band in the header. For now, default to projectCode
		// to
		// preserve the existing demo behavior until tenant/project separation is added.
		String tenantId = (tenantIdHeader != null && !tenantIdHeader.isBlank()) ? tenantIdHeader
				: String.valueOf(projectCode);

		log.info(
				"checkpoint=api.ingest_request tenantId={} projectCode={} workflowCode={} idempotencyKey={} traceParentPresent={}",
				tenantId, projectCode, req.processDefinitionCode(), req.idempotencyKey(),
				traceParent != null && !traceParent.isBlank());

		String payloadJson;
		try {
			ObjectNode node = mapper.valueToTree(req);
			if (traceParent != null && !traceParent.isBlank()) {
				node.put("traceParent", traceParent);
			}
			payloadJson = mapper.writeValueAsString(node);
		} catch (Exception e) {
			payloadJson = "{}";
		}

		StartWorkflowUseCase.Response r = startWorkflowUseCase.execute(new StartWorkflowUseCase.Request(tenantId,
				req.processDefinitionCode(), 1, req.idempotencyKey(), payloadJson));

		if (!r.accepted()) {
			log.warn("checkpoint=api.ingest_rejected tenantId={} workflowCode={} rejectionType={} message={}", tenantId,
					req.processDefinitionCode(), r.rejectionType(), r.message());
			return ApiResponse.error(429, r.rejectionType() + ": " + r.message());
		}
		log.info("checkpoint=api.ingest_accepted tenantId={} workflowCode={} commandId={}", tenantId,
				req.processDefinitionCode(), r.commandId());

		// At ingestion time we don't have a workflow instance id yet (master will
		// create it).
		// We return a "SUBMITTED" placeholder. (Correlation is the commandId, but the
		// current
		// WorkflowInstanceDto shape does not carry it.)
		var dto = new WorkflowInstanceDto(0L, req.processDefinitionCode(), 1, "SUBMITTED", Instant.now(), null);
		return ApiResponse.ok(dto);
	}

	@Operation(summary = "List workflow instances (placeholder)", description = "Returns a paginated list of workflow instances for a project.\n\nNote: The current implementation is a demo placeholder; replace with real query implementation when the scheduling plane is completed.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of instances.") })
	@GetMapping
	public ApiResponse<PageResponse<WorkflowInstanceDto>> listInstances(@PathVariable long projectCode,
			@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "10") int pageSize,
			@RequestParam(required = false) Long processDefinitionCode) {
		var items = List.of(new WorkflowInstanceDto(10001L, 900001L, 1, "RUNNING", Instant.now(), null));
		return ApiResponse.ok(new PageResponse<>(1, pageNo, pageSize, items));
	}

	@Operation(summary = "Control a workflow instance (placeholder)", description = "Control-plane action for an existing workflow instance. Supported actions are STOP / PAUSE / RESUME.\n\nNote: This is a demo placeholder; wiring is present but no-op.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Action accepted (no-op in demo).") })
	@PostMapping("/actions")
	public ApiResponse<Void> control(@PathVariable long projectCode, @RequestParam long processInstanceId,
			@RequestParam String executeType) {
		// TODO: executeType: STOP/PAUSE/RESUME (implementation in later phases)
		return ApiResponse.ok(null);
	}
}
