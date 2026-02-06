
package com.acme.scheduler.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.scheduler.service.workflowdef.UpsertWorkflowDefinitionUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/workflow-definitions")
@Tag(name = "Workflow Definitions", description = "Create or update workflow DAG definitions (tasks + edges).")
public class WorkflowDefinitionController {

  private final UpsertWorkflowDefinitionUseCase useCase;
  private final ObjectMapper mapper;

  public WorkflowDefinitionController(UpsertWorkflowDefinitionUseCase useCase, ObjectMapper mapper) {
    this.useCase = useCase;
    this.mapper = mapper;
  }

  @Operation(
    summary = "Upsert a workflow definition",
    description = "Creates a new workflow definition or updates an existing one. The request carries task definitions and DAG edges as JSON.\n\nTenant routing: provide tenant id via the X-Tenant-Id request header.\n\nNotes: this endpoint only persists the definition; execution is started separately via the execution APIs."
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Workflow definition persisted (workflowCode + workflowVersion returned).",
        content = @Content(schema = @Schema(implementation = CreateWorkflowDefinitionResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validation error / malformed JSON."),
    @ApiResponse(responseCode = "500", description = "Server error while persisting the definition.")
})
  @PostMapping
  public ResponseEntity<?> create(
      @RequestHeader("X-Tenant-Id") String tenantId,
      @RequestBody CreateWorkflowDefinitionRequest req
  ) throws Exception {
    JsonNode tasks = mapper.readTree(req.getTaskDefinitionJson() == null ? "[]" : req.getTaskDefinitionJson());
    JsonNode edges = mapper.readTree(req.getTaskRelationJson() == null ? "[]" : req.getTaskRelationJson());

    var r = useCase.handle(
        tenantId,
        req.getName(),
        tasks,
        edges,
        req.getWorkflowCode()
    );

    return ResponseEntity.ok(new CreateWorkflowDefinitionResponse(r.workflowCode(), r.workflowVersion()));
  }

  public static class CreateWorkflowDefinitionRequest {
    private String name;
    private String taskDefinitionJson;
    private String taskRelationJson;
    private Long workflowCode;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTaskDefinitionJson() { return taskDefinitionJson; }
    public void setTaskDefinitionJson(String taskDefinitionJson) { this.taskDefinitionJson = taskDefinitionJson; }
    public String getTaskRelationJson() { return taskRelationJson; }
    public void setTaskRelationJson(String taskRelationJson) { this.taskRelationJson = taskRelationJson; }
    public Long getWorkflowCode() { return workflowCode; }
    public void setWorkflowCode(Long workflowCode) { this.workflowCode = workflowCode; }
  }

  public record CreateWorkflowDefinitionResponse(long workflowCode, int workflowVersion) {}
}
