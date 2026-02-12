package com.acme.scheduler.master.runtime;

import com.acme.scheduler.domain.alert.AlertPublisher;
import com.acme.scheduler.master.adapter.jdbc.JdbcDagTaskInstanceRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcTriggerRepository;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.meter.SchedulerMeter;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RetryPlannerTest {

  private static MasterMetrics metrics() {
    SchedulerMeter m = new SchedulerMeter() {
      @Override public Counter counter(String name, String description) {
        return (value, labels) -> { /* no-op */ };
      }
      @Override public Histogram histogram(String name, String description, String unit) {
        return (value, labels) -> { /* no-op */ };
      }
    };
    return new MasterMetrics(m);
  }

  @Test
  void computeNextRun_is_in_the_future() {
    var tasks = mock(JdbcDagTaskInstanceRepository.class);
    var triggers = mock(JdbcTriggerRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var wi = new JdbcTemplateWorkflowInstanceStatus(jdbc);
    var alerts = mock(AlertPublisher.class);

    var p = new RetryPlanner(tasks, triggers, wi, alerts, metrics());

    Instant before = Instant.now();
    Instant next = p.computeNextRun(1, Duration.ofMillis(50), Duration.ofSeconds(5));
    assertThat(next).isAfter(before);
  }

  @Test
  void scheduleRetry_noop_when_task_meta_missing() {
    var tasks = mock(JdbcDagTaskInstanceRepository.class);
    var triggers = mock(JdbcTriggerRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var wi = new JdbcTemplateWorkflowInstanceStatus(jdbc);
    var alerts = mock(AlertPublisher.class);

    when(tasks.findTaskMetaByInstanceId(123L)).thenReturn(Optional.empty());

    var p = new RetryPlanner(tasks, triggers, wi, alerts, metrics());
    p.scheduleRetry(10L, 123L, "r", Duration.ofMillis(10), Duration.ofSeconds(1));

    verify(tasks, never()).insertRetryAttemptIfAbsent(anyLong(), anyLong(), anyInt(), anyString(), anyString(), anyInt(), anyInt(), anyString(), any());
    verify(tasks, never()).moveToDlq(anyLong(), anyLong(), anyString());
    verify(triggers, never()).scheduleTrigger(anyLong(), any());
    verify(alerts, never()).publish(any());
  }

  @Test
  void scheduleRetry_moves_to_dlq_and_publishes_alert_when_retry_budget_exhausted() {
    var tasks = mock(JdbcDagTaskInstanceRepository.class);
    var triggers = mock(JdbcTriggerRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var wi = new JdbcTemplateWorkflowInstanceStatus(jdbc);
    var alerts = mock(AlertPublisher.class);

    long workflowInstanceId = 10L;
    long taskInstanceId = 99L;

    when(tasks.findTaskMetaByInstanceId(taskInstanceId)).thenReturn(Optional.of(
        new JdbcDagTaskInstanceRepository.TaskMeta(taskInstanceId, 910050L, 1, "A", "SCRIPT", 2, 3, "{}")
    ));

    // JdbcTemplateWorkflowInstanceStatus.loadInstanceKey uses queryForObject(..., RowMapper, id)
    doReturn(new JdbcTemplateWorkflowInstanceStatus.InstanceKey("default", 123L, 1))
        .when(jdbc).queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(workflowInstanceId));

    var p = new RetryPlanner(tasks, triggers, wi, alerts, metrics());

    p.scheduleRetry(workflowInstanceId, taskInstanceId, "boom", Duration.ofMillis(10), Duration.ofSeconds(1));

    verify(tasks).moveToDlq(workflowInstanceId, taskInstanceId, "boom");
    verify(triggers, never()).scheduleTrigger(anyLong(), any());
    verify(alerts).publish(any());
  }

  @Test
  void scheduleRetry_inserts_next_attempt_and_schedules_trigger_when_budget_remains() {
    var tasks = mock(JdbcDagTaskInstanceRepository.class);
    var triggers = mock(JdbcTriggerRepository.class);
    var jdbc = mock(JdbcTemplate.class);
    var wi = new JdbcTemplateWorkflowInstanceStatus(jdbc);
    var alerts = mock(AlertPublisher.class);

    long workflowInstanceId = 10L;
    long taskInstanceId = 99L;

    when(tasks.findTaskMetaByInstanceId(taskInstanceId)).thenReturn(Optional.of(
        new JdbcDagTaskInstanceRepository.TaskMeta(taskInstanceId, 910050L, 1, "A", "SCRIPT", 0, 3, "{\"k\":\"v\"}")
    ));

    var p = new RetryPlanner(tasks, triggers, wi, alerts, metrics());

    p.scheduleRetry(workflowInstanceId, taskInstanceId, "fail", Duration.ofMillis(10), Duration.ofSeconds(1));

    // Next attempt = 1
    verify(tasks).insertRetryAttemptIfAbsent(
        eq(workflowInstanceId),
        eq(910050L),
        eq(1),
        eq("A"),
        eq("SCRIPT"),
        eq(1),
        eq(3),
        eq("{\"k\":\"v\"}"),
        any(Instant.class)
    );
    verify(triggers).scheduleTrigger(eq(workflowInstanceId), any(Instant.class));
    verify(alerts, never()).publish(any());
    verify(tasks, never()).moveToDlq(anyLong(), anyLong(), anyString());
  }
}
