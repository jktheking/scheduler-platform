package com.acme.scheduler.master.runtime;

import com.acme.scheduler.common.runtime.TaskDispatchEnvelope;
import com.acme.scheduler.master.adapter.jdbc.JdbcDagEdgeRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcDagTaskInstanceRepository;
import com.acme.scheduler.master.kafka.KafkaReadyPublisher;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

public final class DagProgressionEngine {

  private static final Logger log = LoggerFactory.getLogger(DagProgressionEngine.class);

  private final JdbcDagEdgeRepository edges;
  private final JdbcDagTaskInstanceRepository tasks;
  private final JdbcTemplateWorkflowInstanceStatus wi;
  private final KafkaReadyPublisher readyPublisher;
  private final ObjectMapper mapper;
  private final MasterMetrics metrics;

  public DagProgressionEngine(JdbcDagEdgeRepository edges,
                              JdbcDagTaskInstanceRepository tasks,
                              JdbcTemplateWorkflowInstanceStatus wi,
                              KafkaReadyPublisher readyPublisher,
                              ObjectMapper mapper,
                              MasterMetrics metrics) {
    this.edges = Objects.requireNonNull(edges);
    this.tasks = Objects.requireNonNull(tasks);
    this.wi = Objects.requireNonNull(wi);
    this.readyPublisher = Objects.requireNonNull(readyPublisher);
    this.mapper = Objects.requireNonNull(mapper);
    this.metrics = Objects.requireNonNull(metrics);
  }

  /**
   * Called when a task attempt 0 succeeds/skips.
   */
  public void onTaskTerminalSuccess(long workflowInstanceId,
                                   String tenantId,
                                   long workflowCode,
                                   int workflowVersion,
                                   long completedTaskCode,
                                   long triggerId,
                                   Instant dueTime) {
    Map<Long, Set<Long>> outgoing = edges.outgoing(workflowCode, workflowVersion);
    Set<Long> children = outgoing.getOrDefault(completedTaskCode, Set.of());
    if (children.isEmpty()) {
      maybeCompleteWorkflow(workflowInstanceId);
      return;
    }

    long unblocked = 0;
    for (long child : children) {
      // check all parents are terminal success/skip (attempt=0)
      Set<Long> parents = edges.incomingOf(workflowCode, workflowVersion, child);
      if (parents.isEmpty()) {
        // should not happen for child, but allow
      } else {
        int unmet = wi.countNotSuccessfulParents(workflowInstanceId, parents);
        if (unmet > 0) continue;
      }

      // move child to QUEUED (attempt=0)
      int updated = tasks.markQueuedAttempt0(workflowInstanceId, List.of(child));
      if (updated <= 0) continue;

      var metaOpt = tasks.findDispatchMetaByWorkflowAndTaskCode(workflowInstanceId, child, 0);
      if (metaOpt.isEmpty()) continue;
      var t = metaOpt.get();
      try {
        TaskDispatchEnvelope env = new TaskDispatchEnvelope(
            t.workflowInstanceId(), t.taskInstanceId(), t.attempt(), t.tenantId(), t.taskType(), dueTime,
            t.payloadJson(), t.traceParent()
        );
        String envJson = mapper.writeValueAsString(env);
        readyPublisher.publish(new TaskReadyEvent(workflowInstanceId, triggerId, dueTime, envJson));
        metrics.dagEnqueueCount.add(1);
        unblocked++;
      } catch (Exception e) {
        metrics.readyPublishError.add(1);
      }
    }

    if (unblocked > 0) {
      metrics.dagProgressUnblocked.add(unblocked);
      log.info("DAG unblocked workflowInstanceId={} count={}", workflowInstanceId, unblocked);
    }

    maybeCompleteWorkflow(workflowInstanceId);
  }

  private void maybeCompleteWorkflow(long workflowInstanceId) {
    // If all attempt0 tasks are SUCCESS/SKIPPED or terminal, mark workflow SUCCESS.
    if (wi.allAttempt0TerminalSuccess(workflowInstanceId)) {
      wi.markWorkflowSuccess(workflowInstanceId);
    }
  }
}
