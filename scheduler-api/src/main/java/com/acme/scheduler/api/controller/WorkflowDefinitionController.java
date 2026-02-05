
package com.acme.scheduler.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.scheduler.service.workflowdef.UpsertWorkflowDefinitionUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/scheduler/workflow-definitions")
public class WorkflowDefinitionController {

  private final UpsertWorkflowDefinitionUseCase useCase;
  private final ObjectMapper mapper;

  public WorkflowDefinitionController(UpsertWorkflowDefinitionUseCase useCase, ObjectMapper mapper) {
    this.useCase = useCase;
    this.mapper = mapper;
  }

  @PostMapping("/create")
  public ResponseEntity<?> create(@RequestBody CreateWorkflowDefinitionRequest req) throws Exception {
    JsonNode tasks = mapper.readTree(req.getTaskDefinitionJson() == null ? "[]" : req.getTaskDefinitionJson());
    JsonNode edges = mapper.readTree(req.getTaskRelationJson() == null ? "[]" : req.getTaskRelationJson());

    var r = useCase.handle(
        req.getTenantId(),
        req.getName(),
        tasks,
        edges,
        req.getWorkflowCode()
    );

    return ResponseEntity.ok(new CreateWorkflowDefinitionResponse(r.workflowCode(), r.workflowVersion()));
  }

  public static class CreateWorkflowDefinitionRequest {
    private String tenantId;
    private String name;
    private String taskDefinitionJson;
    private String taskRelationJson;
    private Long workflowCode;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
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
