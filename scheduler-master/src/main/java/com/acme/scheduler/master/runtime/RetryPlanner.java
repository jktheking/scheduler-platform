package com.acme.scheduler.master.runtime;

import com.acme.scheduler.master.adapter.jdbc.JdbcDagTaskInstanceRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcTriggerRepository;
import com.acme.scheduler.master.observability.MasterMetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Random;

/** Computes jittered backoff and schedules retry triggers. */
public final class RetryPlanner {

  private final JdbcDagTaskInstanceRepository tasks;
  private final JdbcTriggerRepository triggers;
  private final MasterMetrics metrics;
  private final Random rnd = new Random();

  public RetryPlanner(JdbcDagTaskInstanceRepository tasks,
                      JdbcTriggerRepository triggers,
                      MasterMetrics metrics) {
    this.tasks = Objects.requireNonNull(tasks);
    this.triggers = Objects.requireNonNull(triggers);
    this.metrics = Objects.requireNonNull(metrics);
  }

  public Instant computeNextRun(int attempt, Duration base, Duration max) {
    long pow = 1L << Math.min(20, Math.max(0, attempt));
    long baseMs = base.toMillis();
    long raw = baseMs * pow;
    long capped = Math.min(raw, max.toMillis());
    long jitter = (long) (capped * (0.2 * rnd.nextDouble()));
    return Instant.now().plusMillis(capped + jitter);
  }

  /**
   * Create next attempt row (attempt+1) and schedule a wakeup trigger due at nextRunTime.
   */
  public void scheduleRetry(long workflowInstanceId,
                            long taskInstanceId,
                            String reason,
                            Duration base,
                            Duration max) {
    var metaOpt = tasks.findTaskMetaByInstanceId(taskInstanceId);
    if (metaOpt.isEmpty()) return;
    var meta = metaOpt.get();

    int nextAttempt = meta.attempt() + 1;
    if (nextAttempt >= meta.maxAttempts()) {
      tasks.moveToDlq(workflowInstanceId, taskInstanceId, reason);
      metrics.dlqCreated.add(1);
      return;
    }

    Instant nextRun = computeNextRun(nextAttempt, base, max);

    // Insert new attempt row idempotently.
    tasks.insertRetryAttemptIfAbsent(workflowInstanceId, meta.taskCode(), meta.taskVersion(), meta.taskName(), meta.taskType(), nextAttempt, meta.maxAttempts(), meta.payloadJson(), nextRun);

    // Also schedule a workflow trigger to wake up due-time path.
    triggers.scheduleTrigger(workflowInstanceId, nextRun);
    metrics.retryScheduled.add(1);
  }
}
