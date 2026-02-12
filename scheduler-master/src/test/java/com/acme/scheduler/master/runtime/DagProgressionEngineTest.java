package com.acme.scheduler.master.runtime;

import com.acme.scheduler.master.adapter.jdbc.JdbcDagEdgeRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcDagTaskInstanceRepository;
import com.acme.scheduler.master.kafka.KafkaReadyPublisher;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.meter.SchedulerMeter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DagProgressionEngineTest {

  @Test
  void onTaskTerminalSuccess_unblocksChildren_andPublishesReadyEvents() throws Exception {
    JdbcDagEdgeRepository edges = mock(JdbcDagEdgeRepository.class);
    JdbcDagTaskInstanceRepository tasks = mock(JdbcDagTaskInstanceRepository.class);
    JdbcTemplateWorkflowInstanceStatus wi = mock(JdbcTemplateWorkflowInstanceStatus.class);
    KafkaReadyPublisher readyPublisher = mock(KafkaReadyPublisher.class);
    // TaskDispatchEnvelope contains java.time.Instant; register JavaTimeModule for JSON.
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // Metrics with no-op counters so the engine can call add().
    SchedulerMeter meter = new SchedulerMeter() {
      @Override public Counter counter(String name, String description) { return (v, labels) -> {}; }
      @Override public Histogram histogram(String name, String description, String unit) { return (v, labels) -> {}; }
    };
    MasterMetrics metrics = new MasterMetrics(meter);

    long workflowCode = 100;
    int workflowVersion = 1;
    long parent = 10;
    long child1 = 11;
    long child2 = 12;

    when(edges.outgoing(workflowCode, workflowVersion))
        .thenReturn(Map.of(parent, new LinkedHashSet<>(List.of(child1, child2))));
    when(edges.incomingOf(workflowCode, workflowVersion, child1)).thenReturn(Set.of(parent));
    when(edges.incomingOf(workflowCode, workflowVersion, child2)).thenReturn(Set.of(parent));
    when(wi.countNotSuccessfulParents(anyLong(), anySet())).thenReturn(0);

    // Each child moves to QUEUED.
    when(tasks.markQueuedAttempt0(anyLong(), eq(List.of(child1)))).thenReturn(1);
    when(tasks.markQueuedAttempt0(anyLong(), eq(List.of(child2)))).thenReturn(1);

    when(tasks.findDispatchMetaByWorkflowAndTaskCode(anyLong(), eq(child1), eq(0)))
        .thenReturn(Optional.of(new JdbcDagTaskInstanceRepository.DispatchMeta(
            1L, 101L, 0, "t1", "HTTP", "{}", "tp")));
    when(tasks.findDispatchMetaByWorkflowAndTaskCode(anyLong(), eq(child2), eq(0)))
        .thenReturn(Optional.of(new JdbcDagTaskInstanceRepository.DispatchMeta(
            1L, 102L, 0, "t1", "SCRIPT", "{}", "tp")));

    when(wi.allAttempt0TerminalSuccess(anyLong())).thenReturn(false);

    DagProgressionEngine engine = new DagProgressionEngine(edges, tasks, wi, readyPublisher, mapper, metrics);

    engine.onTaskTerminalSuccess(1L, "t1", workflowCode, workflowVersion, parent, 77L, Instant.parse("2026-02-13T00:00:00Z"));

    ArgumentCaptor<TaskReadyEvent> cap = ArgumentCaptor.forClass(TaskReadyEvent.class);
    verify(readyPublisher, times(2)).publish(cap.capture());
    assertEquals(2, cap.getAllValues().size());
    // TaskReadyEvent carries the ready-task envelope as JSON payload.
    assertTrue(cap.getAllValues().get(0).payloadJson().contains("workflowInstanceId"));
    assertTrue(cap.getAllValues().get(1).payloadJson().contains("taskInstanceId"));

    // Completion check is always evaluated.
    verify(wi).allAttempt0TerminalSuccess(1L);
    verify(wi, never()).markWorkflowSuccess(anyLong());
  }

  @Test
  void onTaskTerminalSuccess_whenNoChildren_maybeCompletesWorkflow() {
    JdbcDagEdgeRepository edges = mock(JdbcDagEdgeRepository.class);
    JdbcDagTaskInstanceRepository tasks = mock(JdbcDagTaskInstanceRepository.class);
    JdbcTemplateWorkflowInstanceStatus wi = mock(JdbcTemplateWorkflowInstanceStatus.class);
    KafkaReadyPublisher readyPublisher = mock(KafkaReadyPublisher.class);
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    SchedulerMeter meter = new SchedulerMeter() {
      @Override public Counter counter(String name, String description) { return (v, labels) -> {}; }
      @Override public Histogram histogram(String name, String description, String unit) { return (v, labels) -> {}; }
    };
    MasterMetrics metrics = new MasterMetrics(meter);

    when(edges.outgoing(100L, 1)).thenReturn(Collections.emptyMap());
    when(wi.allAttempt0TerminalSuccess(1L)).thenReturn(true);

    DagProgressionEngine engine = new DagProgressionEngine(edges, tasks, wi, readyPublisher, mapper, metrics);
    engine.onTaskTerminalSuccess(1L, "t1", 100L, 1, 10L, 1L, Instant.now());

    verify(wi).markWorkflowSuccess(1L);
    verifyNoInteractions(readyPublisher);
  }
}
