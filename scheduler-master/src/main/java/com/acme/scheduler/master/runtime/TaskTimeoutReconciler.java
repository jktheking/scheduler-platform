package com.acme.scheduler.master.runtime;

import com.acme.scheduler.common.alert.AlertEvent;
import com.acme.scheduler.common.alert.AlertSeverity;
import com.acme.scheduler.common.alert.AlertType;
import com.acme.scheduler.domain.alert.AlertPublisher;
import com.acme.scheduler.master.adapter.jdbc.JdbcDagTaskInstanceRepository;
import com.acme.scheduler.master.ha.ShardLeaderElector;
import com.acme.scheduler.master.observability.MasterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Master-owned timeout policy (alerting only).
 *
 * Worker is responsible for enforcing hard timeouts during execution.
 * Master reconciler detects tasks that appear to have exceeded their configured timeout and emits alerts once.
 */
public final class TaskTimeoutReconciler {

  private static final Logger log = LoggerFactory.getLogger(TaskTimeoutReconciler.class);

  private final JdbcDagTaskInstanceRepository tasks;
  private final AlertPublisher alertPublisher;
  private final ShardLeaderElector shardElector;
  private final MasterMetrics metrics;
  private final long pollMs;
  private final int batch;

  private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r);
    t.setName("task-timeout-reconciler");
    t.setDaemon(true);
    return t;
  });

  public TaskTimeoutReconciler(JdbcDagTaskInstanceRepository tasks,
                              AlertPublisher alertPublisher,
                              ShardLeaderElector shardElector,
                              MasterMetrics metrics,
                              long pollMs,
                              int batch) {
    this.tasks = Objects.requireNonNull(tasks);
    this.alertPublisher = Objects.requireNonNull(alertPublisher);
    this.shardElector = Objects.requireNonNull(shardElector);
    this.metrics = Objects.requireNonNull(metrics);
    this.pollMs = pollMs;
    this.batch = batch;
  }

  public void start() {
    exec.scheduleWithFixedDelay(this::tick, pollMs, pollMs, TimeUnit.MILLISECONDS);
    log.info("TaskTimeoutReconciler started pollMs={} batch={}", pollMs, batch);
  }

  public void stop() {
    exec.shutdownNow();
    log.info("TaskTimeoutReconciler stopped");
  }

  private void tick() {
    try {
      // Only the shard leader(s) should perform timeout scanning and alert emission.
      var leaderShards = shardElector.leaderShards();
      if (leaderShards.isEmpty()) return;
      List<JdbcDagTaskInstanceRepository.TimedOutRunningTask> rows = tasks.selectTimedOutRunningTasksForShards(leaderShards, batch);
      for (var r : rows) {
        try {
          String eventId = "TASK_TIMEOUT:" + r.taskInstanceId();
          String details = "startedAt=" + r.startedAt() + ", timeoutMs=" + r.timeoutMs();
          alertPublisher.publish(new AlertEvent(
              eventId,
              AlertType.TASK_TIMEOUT,
              AlertSeverity.ERROR,
              r.tenantId(),
              r.workflowInstanceId(),
              r.workflowCode(),
              r.workflowVersion(),
              r.taskInstanceId(),
              r.taskCode(),
              r.attempt(),
              null,
              Instant.now(),
              "Task appears to have exceeded timeout",
              details,
              r.traceParent()
          ));
          tasks.markTimeoutAlerted(r.taskInstanceId());
          metrics.taskTimeoutAlerted.add(1);
          log.warn("checkpoint=master.task_timeout_alerted workflowInstanceId={} taskInstanceId={} timeoutMs={} startedAt={}",
              r.workflowInstanceId(), r.taskInstanceId(), r.timeoutMs(), r.startedAt());
        } catch (Exception e) {
          metrics.taskTimeoutAlertError.add(1);
          log.warn("checkpoint=master.task_timeout_alert_failed taskInstanceId={} error={}", r.taskInstanceId(), e.toString());
        }
      }
    } catch (Exception e) {
      metrics.taskTimeoutAlertError.add(1);
      log.warn("TaskTimeoutReconciler tick error: {}", e.toString());
    }
  }
}
