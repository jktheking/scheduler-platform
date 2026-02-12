package com.acme.scheduler.api.controller;

import com.acme.scheduler.service.workflowdef.UpsertWorkflowDefinitionUseCase;
import com.acme.scheduler.service.workflowdef.UpsertWorkflowDefinitionUseCase.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test (no Spring test slice) to avoid requiring spring-boot-test-autoconfigure.
 */
class WorkflowDefinitionControllerUnitTest {

  @Test
  void create_parses_json_and_invokes_usecase() throws Exception {
    UpsertWorkflowDefinitionUseCase useCase = mock(UpsertWorkflowDefinitionUseCase.class);
    ObjectMapper mapper = new ObjectMapper();
    when(useCase.handle(anyString(), anyString(), any(), any(), any(), any()))
        .thenReturn(new Response(123L, 7));

    var ctrl = new WorkflowDefinitionController(useCase, mapper);

    var req = new WorkflowDefinitionController.CreateWorkflowDefinitionRequest();
    req.setName("wf");
    req.setSlaSeconds(300L);
    req.setTaskDefinitionJson("[{\"name\":\"A\"}]");
    req.setTaskRelationJson("[]");
    req.setWorkflowCode(null);

    var resp = ctrl.create("default", req);
    assertThat(resp.getStatusCode().value()).isEqualTo(200);
    var body = (WorkflowDefinitionController.CreateWorkflowDefinitionResponse) resp.getBody();
    assertThat(body.workflowCode()).isEqualTo(123L);
    assertThat(body.workflowVersion()).isEqualTo(7);

    verify(useCase).handle(eq("default"), eq("wf"), eq(300L), any(), any(), isNull());
  }
}
