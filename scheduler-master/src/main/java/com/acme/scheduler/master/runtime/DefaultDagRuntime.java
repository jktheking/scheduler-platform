package com.acme.scheduler.master.runtime;

import com.acme.scheduler.common.runtime.TaskDispatchEnvelope;
import com.acme.scheduler.common.runtime.TaskStateEvent;
import com.acme.scheduler.master.adapter.jdbc.JdbcDagTaskInstanceRepository;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.kafka.KafkaReadyPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class DefaultDagRuntime implements DagRuntime {

  private static final Logger log = LoggerFactory.getLogger(DefaultDagRuntime.class);

  private final WorkflowMaterializer materializer;
  private final JdbcDagTaskInstanceRepository tasks;
  private final JdbcTemplateWorkflowInstanceStatus wi;
  private final KafkaReadyPublisher readyPublisher;
  private final DagProgressionEngine progression;
  private final RetryPlanner retryPlanner;
  private final ObjectMapper mapper;
  private final MasterMetrics metrics;

  public DefaultDagRuntime(WorkflowMaterializer materializer,
                           JdbcDagTaskInstanceRepository tasks,
                           JdbcTemplateWorkflowInstanceStatus wi,
                           KafkaReadyPublisher readyPublisher,
                           DagProgressionEngine progression,
                           RetryPlanner retryPlanner,
                           ObjectMapper mapper,
                           MasterMetrics metrics) {
    this.materializer = Objects.requireNonNull(materializer);
    this.tasks = Objects.requireNonNull(tasks);
    this.wi = Objects.requireNonNull(wi);
    this.readyPublisher = Objects.requireNonNull(readyPublisher);
    this.progression = Objects.requireNonNull(progression);
    this.retryPlanner = Objects.requireNonNull(retryPlanner);
    this.mapper = Objects.requireNonNull(mapper);
    this.metrics = Objects.requireNonNull(metrics);
  }

  @Override
  public void onTriggerDue(long workflowInstanceId, long triggerId, Instant dueTime, String triggerPayloadJson) {
    var k = wi.loadInstanceKey(workflowInstanceId);
    wi.markWorkflowRunning(workflowInstanceId);

    var m = materializer.materializeIfAbsent(k.tenantId(), k.workflowCode(), k.workflowVersion(), workflowInstanceId);
    metrics.dagMaterializeCount.add(1);

    // Convert due retry-wait tasks to queued.
    tasks.moveRetryWaitToQueued(workflowInstanceId, Instant.now());

    // Ensure roots are queued on first wakeup.
    tasks.markQueuedAttempt0(workflowInstanceId, m.roots());

    // Dispatch all queued tasks (bounded)
    List<JdbcDagTaskInstanceRepository.DispatchableTask> dispatch = tasks.selectDispatchableQueued(workflowInstanceId, Instant.now(), 500);
    for (var t : dispatch) {
      try {
        TaskDispatchEnvelope env = new TaskDispatchEnvelope(
            t.workflowInstanceId(), t.taskInstanceId(), t.attempt(), t.tenantId(), t.taskType(), dueTime,
            t.payloadJson(), t.traceParent()
        );
        String envJson = mapper.writeValueAsString(env);
        readyPublisher.publish(new TaskReadyEvent(workflowInstanceId, triggerId, dueTime, envJson));
        metrics.dagEnqueueCount.add(1);
      } catch (Exception e) {
        metrics.readyPublishError.add(1);
      }
    }
  }

  @Override
  public void onTaskState(TaskStateEvent evt) {
    if (evt == null) return;
    try {
      var key = wi.loadInstanceKey(evt.workflowInstanceId());
      switch (evt.state()) {
        case "SUCCEEDED", "SKIPPED" -> {
          // We consider attempt0 progression only. If later attempts succeed, they still unblock children of that task.
          var metaOpt = tasks.findTaskMetaByInstanceId(evt.taskInstanceId());
          if (metaOpt.isEmpty()) return;
          var meta = metaOpt.get();
          progression.onTaskTerminalSuccess(evt.workflowInstanceId(), key.tenantId(), key.workflowCode(), key.workflowVersion(), meta.taskCode(), /*triggerId*/0L, Instant.now());
        }
        case "FAILED" -> {
          retryPlanner.scheduleRetry(evt.workflowInstanceId(), evt.taskInstanceId(), evt.errorMessage() == null ? "FAILED" : evt.errorMessage(), Duration.ofSeconds(2), Duration.ofMinutes(5));
        }
        case "DLQ" -> {
          wi.markWorkflowFailed(evt.workflowInstanceId(), evt.errorMessage() == null ? "DLQ" : evt.errorMessage());
        }
        default -> {
          // ignore STARTED etc
        }
      }
    } catch (Exception e) {
      log.warn("onTaskState error: {}", e.toString());
    }
  }
}
