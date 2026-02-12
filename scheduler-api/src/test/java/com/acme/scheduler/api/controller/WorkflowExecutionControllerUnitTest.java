package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.execution.StartWorkflowInstanceRequest;
import com.acme.scheduler.service.workflow.StartWorkflowUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for ingestion controller without Spring MVC test dependencies. */
class WorkflowExecutionControllerUnitTest {

  private static StartWorkflowInstanceRequest minimalReq(String idempotencyKey) {
    return new StartWorkflowInstanceRequest(
        900001L,        // processDefinitionCode
        null,           // scheduleTime
        "START_PROCESS",// execType
        "END",          // failureStrategy
        "NONE",         // warningType
        0L,             // warningGroupId
        "default",      // workerGroup
        0L,             // environmentCode
        "RUN_PARALLEL", // runMode
        "[]",           // startNodeList
        "TASK_ONLY",    // taskDependType
        idempotencyKey  // idempotencyKey
    );
  }

  @Test
  void start_calls_usecase_and_returns_submitted() {
    StartWorkflowUseCase useCase = mock(StartWorkflowUseCase.class);
    when(useCase.execute(any())).thenReturn(new StartWorkflowUseCase.Response(true, "cmd-123", null, null));

    var ctrl = new WorkflowExecutionController(useCase, new ObjectMapper());

    var req = minimalReq("idem-1");
    var resp = ctrl.start(42L, req, "default", "00-abc-xyz-01");

    assertThat(resp.code()).isEqualTo(0);
    assertThat(resp.data()).isNotNull();
    assertThat(resp.data().state()).isEqualTo("SUBMITTED");
    verify(useCase).execute(any());
  }

  @Test
  void start_rejected_returns_429_error() {
    StartWorkflowUseCase useCase = mock(StartWorkflowUseCase.class);
    when(useCase.execute(any())).thenReturn(new StartWorkflowUseCase.Response(false, "", "ADMISSION", "too_many"));

    var ctrl = new WorkflowExecutionController(useCase, new ObjectMapper());
    var req = minimalReq("idem-1");

    var resp = ctrl.start(42L, req, "default", null);
    assertThat(resp.code()).isEqualTo(429);
    assertThat(resp.msg()).contains("ADMISSION");
  }
}
