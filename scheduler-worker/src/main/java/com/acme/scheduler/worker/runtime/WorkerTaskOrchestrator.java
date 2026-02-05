package com.acme.scheduler.worker.runtime;

import com.acme.scheduler.common.runtime.TaskDispatchEnvelope;
import com.acme.scheduler.common.runtime.TaskStateEvent;
import com.acme.scheduler.meter.SchedulerMeter;
import com.acme.scheduler.worker.adapter.jdbc.JdbcTaskInstanceRepository;
import com.acme.scheduler.worker.exec.HttpTaskExecutor;
import com.acme.scheduler.worker.exec.ScriptTaskExecutor;
import com.acme.scheduler.worker.kafka.KafkaTaskStatePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WorkerTaskOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(WorkerTaskOrchestrator.class);

  private final JdbcTaskInstanceRepository repo;
  private final KafkaTaskStatePublisher statePublisher;
  private final HttpTaskExecutor http;
  private final ScriptTaskExecutor script;
  private final SchedulerMeter meter;
  private final ExecutorService exec;

  private final SchedulerMeter.Counter taskClaimed;
  private final SchedulerMeter.Counter taskStarted;
  private final SchedulerMeter.Counter taskSuccess;
  private final SchedulerMeter.Counter taskFail;
  private final SchedulerMeter.Histogram taskRuntimeMs;
  private final SchedulerMeter.Histogram taskQueueDelayMs;

  public WorkerTaskOrchestrator(JdbcTaskInstanceRepository repo,
                               KafkaTaskStatePublisher statePublisher,
                               HttpTaskExecutor http,
                               ScriptTaskExecutor script,
                               SchedulerMeter meter) {
    this.repo = Objects.requireNonNull(repo);
    this.statePublisher = Objects.requireNonNull(statePublisher);
    this.http = Objects.requireNonNull(http);
    this.script = Objects.requireNonNull(script);
    this.meter = Objects.requireNonNull(meter);
    this.exec = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("worker-task-", 0).factory());

    this.taskClaimed = meter.counter("scheduler.worker.task.claimed.count", "Tasks claimed");
    this.taskStarted = meter.counter("scheduler.worker.task.started.count", "Tasks started");
    this.taskSuccess = meter.counter("scheduler.worker.task.success.count", "Tasks succeeded");
    this.taskFail = meter.counter("scheduler.worker.task.fail.count", "Tasks failed");
    this.taskRuntimeMs = meter.histogram("scheduler.worker.task.runtime.ms", "Task runtime", "ms");
    this.taskQueueDelayMs = meter.histogram("scheduler.worker.task.queue_delay.ms", "Queue delay", "ms");
  }

  public boolean handle(TaskDispatchEnvelope env, String workerId) {
    if (env == null) return true;

    // Try claim on the caller thread so the Kafka consumer can safely commit offsets only
    // after we have durably claimed this task in DB (at-least-once -> effectively-once).
    boolean claimed = repo.tryClaim(env.taskInstanceId(), env.attempt(), workerId);
    if (!claimed) {
      return true; // duplicate delivery / already claimed
    }
    taskClaimed.add(1, "taskType", env.taskType());

    exec.execute(() -> runClaimed(env, workerId));
    return true;
  }

  private void runClaimed(TaskDispatchEnvelope env, String workerId) {
    Instant now = Instant.now();
    long wi = env.workflowInstanceId();
    long ti = env.taskInstanceId();

    // queue delay: dueTime -> now
    if (env.dueTime() != null) {
      long qd = Math.max(0, now.toEpochMilli() - env.dueTime().toEpochMilli());
      taskQueueDelayMs.record(qd, "taskType", env.taskType());
    }
repo.markRunning(ti);
    Instant started = Instant.now();
    taskStarted.add(1, "taskType", env.taskType());
    statePublisher.publish(new TaskStateEvent(wi, ti, env.attempt(), "STARTED", started, null, null, null, null, env.traceParent()));

    try {
      TaskExecutionResult res;
      if ("HTTP".equalsIgnoreCase(env.taskType())) {
        var r = http.execute(env.payloadJson());
        res = new TaskExecutionResult(r.statusCode(), null);
      } else if ("SCRIPT".equalsIgnoreCase(env.taskType())) {
        var r = script.execute(env.payloadJson());
        res = new TaskExecutionResult(null, r.exitCode());
      } else {
        throw new IllegalArgumentException("Unsupported taskType " + env.taskType());
      }

      repo.markSuccess(ti);
      Instant finished = Instant.now();
      taskRuntimeMs.record(Math.max(0, finished.toEpochMilli() - started.toEpochMilli()), "taskType", env.taskType());
      taskSuccess.add(1, "taskType", env.taskType());
      statePublisher.publish(new TaskStateEvent(wi, ti, env.attempt(), "SUCCEEDED", started, finished, res.httpStatus(), res.exitCode(), null, env.traceParent()));
    } catch (Exception e) {
      repo.markFailure(ti, e.toString());
      Instant finished = Instant.now();
      taskRuntimeMs.record(Math.max(0, finished.toEpochMilli() - started.toEpochMilli()), "taskType", env.taskType());
      taskFail.add(1, "taskType", env.taskType());
      statePublisher.publish(new TaskStateEvent(wi, ti, env.attempt(), "FAILED", started, finished, null, null, e.toString(), env.traceParent()));
      log.warn("Task failed workflowInstanceId={} taskInstanceId={} attempt={} err={}", wi, ti, env.attempt(), e.toString());
    }
  }

  public record TaskExecutionResult(Integer httpStatus, Integer exitCode) {}
}
