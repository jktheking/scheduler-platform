package com.acme.scheduler.master.runtime;

import com.acme.scheduler.common.runtime.TaskDispatchEnvelope;
import com.acme.scheduler.master.kafka.KafkaReadyPublisher;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodic repair loop for ready queue.
 */
public final class TaskEnqueueReconciler {

  private static final Logger log = LoggerFactory.getLogger(TaskEnqueueReconciler.class);

  private final JdbcTemplate jdbc;
  private final KafkaReadyPublisher publisher;
  private final ObjectMapper mapper;
  private final MasterMetrics metrics;
  private final long reenqueueAfterMs;

  private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r);
    t.setName("task-enqueue-reconciler");
    t.setDaemon(true);
    return t;
  });

  public TaskEnqueueReconciler(JdbcTemplate jdbc,
                              KafkaReadyPublisher publisher,
                              ObjectMapper mapper,
                              MasterMetrics metrics,
                              long reenqueueAfterMs) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.publisher = Objects.requireNonNull(publisher);
    this.mapper = Objects.requireNonNull(mapper);
    this.metrics = Objects.requireNonNull(metrics);
    this.reenqueueAfterMs = reenqueueAfterMs;
  }

  public void start() {
    exec.scheduleWithFixedDelay(this::tick, 5_000, 5_000, TimeUnit.MILLISECONDS);
  }

  public void stop() {
    exec.shutdownNow();
  }

  private void tick() {
    try {
      Instant cutoff = Instant.now().minusMillis(reenqueueAfterMs);
      var rows = jdbc.query("""
        SELECT wi.workflow_instance_id,
               wi.tenant_id,
               ti.task_instance_id,
               ti.attempt,
               ti.task_type,
               ti.payload_json::text,
               COALESCE(wp.plan_json->>'traceParent','') AS trace_parent,
               ti.updated_at
        FROM t_task_instance ti
        JOIN t_workflow_instance wi ON wi.workflow_instance_id = ti.workflow_instance_id
        LEFT JOIN t_workflow_plan wp ON wp.workflow_instance_id = wi.workflow_instance_id
        WHERE ti.status='QUEUED' AND ti.updated_at < ?
        ORDER BY ti.updated_at ASC
        LIMIT 200
      """, (rs, rowNum) -> new Object[] {
          rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getInt(4), rs.getString(5), rs.getString(6), rs.getString(7)
      }, java.sql.Timestamp.from(cutoff));

      for (Object[] r : rows) {
        long wi = (Long) r[0];
        String tenant = (String) r[1];
        long ti = (Long) r[2];
        int attempt = (Integer) r[3];
        String type = (String) r[4];
        String payload = (String) r[5];
        String traceParent = (String) r[6];
        TaskDispatchEnvelope env = new TaskDispatchEnvelope(wi, ti, attempt, tenant, type, Instant.now(), payload, traceParent);
        String envJson = mapper.writeValueAsString(env);
        publisher.publish(new TaskReadyEvent(wi, 0L, Instant.now(), envJson));
        metrics.dagEnqueueCount.add(1);
      }

      if (!rows.isEmpty()) {
        log.info("Re-enqueued {} stale tasks", rows.size());
      }
    } catch (Exception e) {
      log.warn("TaskEnqueueReconciler tick error: {}", e.toString());
    }
  }
}
