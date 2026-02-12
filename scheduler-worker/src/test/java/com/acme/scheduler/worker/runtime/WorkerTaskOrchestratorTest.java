package com.acme.scheduler.worker.runtime;

import com.acme.scheduler.common.runtime.TaskDispatchEnvelope;
import com.acme.scheduler.common.runtime.TaskStateEvent;
import com.acme.scheduler.meter.SchedulerMeter;
import com.acme.scheduler.worker.adapter.jdbc.JdbcTaskInstanceRepository;
import com.acme.scheduler.worker.exec.HttpTaskExecutor;
import com.acme.scheduler.worker.exec.ScriptTaskExecutor;
import com.acme.scheduler.worker.kafka.KafkaTaskStatePublisher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkerTaskOrchestratorTest {

  @Test
  void handle_claimsOnce_andPublishesSuccess_forHttp() throws Exception {
    JdbcTaskInstanceRepository repo = mock(JdbcTaskInstanceRepository.class);
    when(repo.tryClaim(anyLong(), anyInt(), anyString())).thenReturn(true);

    KafkaTaskStatePublisher publisher = mock(KafkaTaskStatePublisher.class);
    HttpTaskExecutor http = mock(HttpTaskExecutor.class);
    when(http.execute(anyString())).thenReturn(new HttpTaskExecutor.Result(200, "ok"));
    ScriptTaskExecutor script = mock(ScriptTaskExecutor.class);

    // latch for terminal event
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<TaskStateEvent> terminal = new AtomicReference<>();
    doAnswer(inv -> {
      TaskStateEvent e = inv.getArgument(0);
      if ("SUCCEEDED".equals(e.state()) || "FAILED".equals(e.state())) {
        terminal.set(e);
        done.countDown();
      }
      return null;
    }).when(publisher).publish(any(TaskStateEvent.class));

    SchedulerMeter meter = new SchedulerMeter() {
      @Override public Counter counter(String name, String description) { return (v, labels) -> {}; }
      @Override public Histogram histogram(String name, String description, String unit) { return (v, labels) -> {}; }
    };

    WorkerTaskOrchestrator orch = new WorkerTaskOrchestrator(repo, publisher, http, script, meter);

    TaskDispatchEnvelope env = new TaskDispatchEnvelope(
        1L, 10L, 0, "t1", "HTTP", Instant.now(), "{}", "tp"
    );

    assertTrue(orch.handle(env, "w1"));
    assertTrue(done.await(5, TimeUnit.SECONDS), "expected terminal state publish");

    verify(repo).tryClaim(10L, 0, "w1");
    verify(repo).markRunning(10L);
    verify(repo).markSuccess(10L);
    assertNotNull(terminal.get());
    assertEquals("SUCCEEDED", terminal.get().state());
  }

  @Test
  void handle_skipsWhenAlreadyClaimed() {
    JdbcTaskInstanceRepository repo = mock(JdbcTaskInstanceRepository.class);
    when(repo.tryClaim(anyLong(), anyInt(), anyString())).thenReturn(false);
    KafkaTaskStatePublisher publisher = mock(KafkaTaskStatePublisher.class);
    HttpTaskExecutor http = mock(HttpTaskExecutor.class);
    ScriptTaskExecutor script = mock(ScriptTaskExecutor.class);

    SchedulerMeter meter = new SchedulerMeter() {
      @Override public Counter counter(String name, String description) { return (v, labels) -> {}; }
      @Override public Histogram histogram(String name, String description, String unit) { return (v, labels) -> {}; }
    };

    WorkerTaskOrchestrator orch = new WorkerTaskOrchestrator(repo, publisher, http, script, meter);
    TaskDispatchEnvelope env = new TaskDispatchEnvelope(1L, 10L, 0, "t1", "HTTP", Instant.now(), "{}", "tp");

    assertTrue(orch.handle(env, "w1"));
    verify(repo).tryClaim(10L, 0, "w1");
    verifyNoMoreInteractions(repo);
    verifyNoInteractions(publisher, http, script);
  }
}
