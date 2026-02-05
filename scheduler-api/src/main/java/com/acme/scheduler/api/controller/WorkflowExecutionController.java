package com.acme.scheduler.api.controller;

import java.time.Instant;
import java.util.List;

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

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * WorkflowExecutionController → StartWorkflowUseCase → IngestionGateway (JDBC | Kafka WAL)
 *
 * This controller focuses on ingestion only; the master will later materialize instances/DAG plans.
 */
@RestController
@RequestMapping("/scheduler/projects/{projectCode}/executors")
@Tag(name = "Workflow Execution APIs")
public class WorkflowExecutionController {

  private final StartWorkflowUseCase startWorkflowUseCase;
  private final ObjectMapper mapper;

  public WorkflowExecutionController(StartWorkflowUseCase startWorkflowUseCase, ObjectMapper mapper) {
    this.startWorkflowUseCase = startWorkflowUseCase;
    this.mapper = mapper;
  }

  @PostMapping("/start-process-instance")
  public ApiResponse<WorkflowInstanceDto> start(
      @PathVariable long projectCode,
      @Valid @RequestBody StartWorkflowInstanceRequest req,
      @RequestHeader(value = "traceparent", required = false) String traceParent
  ) {
    String tenantId = String.valueOf(projectCode); // until tenant/project separation is added

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

    StartWorkflowUseCase.Response r = startWorkflowUseCase.execute(
        new StartWorkflowUseCase.Request(
            tenantId,
            req.processDefinitionCode(),
            1,
            req.idempotencyKey(),
            payloadJson
        )
    );

    if (!r.accepted()) {
      return ApiResponse.error(429, r.rejectionType() + ": " + r.message());
    }

    // At ingestion time we don't have a workflow instance id yet (master will create it).
    // We return a "SUBMITTED" placeholder. (Correlation is the commandId, but the current
    // WorkflowInstanceDto shape does not carry it.)
    var dto = new WorkflowInstanceDto(
        0L,
        req.processDefinitionCode(),
        1,
        "SUBMITTED",
        Instant.now(),
        null
    );
    return ApiResponse.ok(dto);
  }

  @GetMapping("/process-instance/list")
  public ApiResponse<PageResponse<WorkflowInstanceDto>> listInstances(
      @PathVariable long projectCode,
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "10") int pageSize,
      @RequestParam(required = false) Long processDefinitionCode
  ) {
    var items = List.of(new WorkflowInstanceDto(10001L, 900001L, 1, "RUNNING", Instant.now(), null));
    return ApiResponse.ok(new PageResponse<>(1, pageNo, pageSize, items));
  }

  @PostMapping("/execute")
  public ApiResponse<Void> control(
      @PathVariable long projectCode,
      @RequestParam long processInstanceId,
      @RequestParam String executeType
  ) {
    // executeType: STOP/PAUSE/RESUME (implementation in later phases)
    return ApiResponse.ok(null);
  }
}
