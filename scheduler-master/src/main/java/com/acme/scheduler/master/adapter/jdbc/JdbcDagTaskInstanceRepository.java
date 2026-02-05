package com.acme.scheduler.master.adapter.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

public final class JdbcDagTaskInstanceRepository {

  public record TaskMeta(
      long taskInstanceId,
      long taskCode,
      int taskVersion,
      String taskName,
      String taskType,
      int attempt,
      int maxAttempts,
      String payloadJson
  ) {}

  public record DispatchableTask(
      long workflowInstanceId,
      long taskInstanceId,
      int attempt,
      String tenantId,
      String taskType,
      String payloadJson,
      String traceParent
  ) {}

  public record DispatchMeta(
      long workflowInstanceId,
      long taskInstanceId,
      int attempt,
      String tenantId,
      String taskType,
      String payloadJson,
      String traceParent
  ) {}

  private final JdbcTemplate jdbc;

  public JdbcDagTaskInstanceRepository(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  public boolean anyForWorkflow(long workflowInstanceId) {
    Integer c = jdbc.queryForObject("SELECT COUNT(1) FROM t_task_instance WHERE workflow_instance_id=?", Integer.class, workflowInstanceId);
    return c != null && c > 0;
  }

  public void insertAttempt0IfAbsent(long workflowInstanceId,
                                    long taskCode,
                                    int taskVersion,
                                    String taskName,
                                    String taskType,
                                    int maxAttempts,
                                    String payloadJson) {
    jdbc.update("""
      INSERT INTO t_task_instance(
        workflow_instance_id, task_code, task_version, task_name, task_type,
        status, attempt, max_attempts, payload_json, created_at, updated_at
      )
      VALUES (?,?,?,?,?, 'PENDING', 0, ?, ?::jsonb, now(), now())
      ON CONFLICT (workflow_instance_id, task_code, attempt) DO NOTHING
    """, workflowInstanceId, taskCode, taskVersion, taskName, taskType, maxAttempts,
        payloadJson == null ? "{}" : payloadJson);
  }

  public int markQueuedAttempt0(long workflowInstanceId, Collection<Long> taskCodes) {
    if (taskCodes == null || taskCodes.isEmpty()) return 0;
    Long[] arr = taskCodes.toArray(Long[]::new);
    return jdbc.update("""
      UPDATE t_task_instance
      SET status='QUEUED', updated_at=now()
      WHERE workflow_instance_id=? AND attempt=0 AND status IN ('PENDING','RETRY_WAIT') AND task_code = ANY (?)
    """, workflowInstanceId, arr);
  }

  public int moveRetryWaitToQueued(long workflowInstanceId, Instant now) {
    return jdbc.update("""
      UPDATE t_task_instance
      SET status='QUEUED', updated_at=now()
      WHERE workflow_instance_id=?
        AND status='RETRY_WAIT'
        AND next_run_time IS NOT NULL
        AND next_run_time <= ?
    """, workflowInstanceId, Timestamp.from(now));
  }

  public List<DispatchableTask> selectDispatchableQueued(long workflowInstanceId, Instant now, int limit) {
    List<DispatchableTask> out = new ArrayList<>();
    SqlRowSet rs = jdbc.queryForRowSet("""
      SELECT wi.workflow_instance_id,
             ti.task_instance_id,
             ti.attempt,
             wi.tenant_id,
             ti.task_type,
             ti.payload_json::text,
             COALESCE(wp.plan_json->>'traceParent','')
      FROM t_task_instance ti
      JOIN t_workflow_instance wi ON wi.workflow_instance_id = ti.workflow_instance_id
      LEFT JOIN t_workflow_plan wp ON wp.workflow_instance_id = wi.workflow_instance_id
      WHERE ti.workflow_instance_id=?
        AND ti.status='QUEUED'
        AND (ti.next_run_time IS NULL OR ti.next_run_time <= ?)
      ORDER BY ti.task_instance_id ASC
      LIMIT ?
    """, workflowInstanceId, Timestamp.from(now), limit);
    while (rs.next()) {
      out.add(new DispatchableTask(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
    }
    return out;
  }

  public Optional<TaskMeta> findTaskMetaByInstanceId(long taskInstanceId) {
    SqlRowSet rs = jdbc.queryForRowSet("""
      SELECT task_instance_id, task_code, task_version, task_name, task_type, attempt, max_attempts, payload_json::text
      FROM t_task_instance
      WHERE task_instance_id=?
    """, taskInstanceId);
    if (!rs.next()) return Optional.empty();
    return Optional.of(new TaskMeta(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getInt(7), rs.getString(8)));
  }

  public Optional<DispatchMeta> findDispatchMetaByWorkflowAndTaskCode(long workflowInstanceId, long taskCode, int attempt) {
    SqlRowSet rs = jdbc.queryForRowSet("""
      SELECT wi.workflow_instance_id,
             ti.task_instance_id,
             ti.attempt,
             wi.tenant_id,
             ti.task_type,
             ti.payload_json::text,
             COALESCE(wp.plan_json->>'traceParent','')
      FROM t_task_instance ti
      JOIN t_workflow_instance wi ON wi.workflow_instance_id = ti.workflow_instance_id
      LEFT JOIN t_workflow_plan wp ON wp.workflow_instance_id = wi.workflow_instance_id
      WHERE ti.workflow_instance_id=?
        AND ti.task_code=?
        AND ti.attempt=?
        AND ti.status='QUEUED'
      ORDER BY ti.task_instance_id ASC
      LIMIT 1
    """, workflowInstanceId, taskCode, attempt);
    if (!rs.next()) return Optional.empty();
    return Optional.of(new DispatchMeta(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
  }

  public void insertRetryAttemptIfAbsent(long workflowInstanceId,
                                        long taskCode,
                                        int taskVersion,
                                        String taskName,
                                        String taskType,
                                        int attempt,
                                        int maxAttempts,
                                        String payloadJson,
                                        Instant nextRunTime) {
    jdbc.update("""
      INSERT INTO t_task_instance(
        workflow_instance_id, task_code, task_version, task_name, task_type,
        status, attempt, max_attempts, next_run_time, payload_json, created_at, updated_at
      )
      VALUES (?,?,?,?,?, 'RETRY_WAIT', ?, ?, ?, ?::jsonb, now(), now())
      ON CONFLICT (workflow_instance_id, task_code, attempt) DO NOTHING
    """, workflowInstanceId, taskCode, taskVersion, taskName, taskType, attempt, maxAttempts, Timestamp.from(nextRunTime), payloadJson == null ? "{}" : payloadJson);
  }

  public void markRetryWait(long workflowInstanceId, long taskCode, int attempt, Instant nextRunTime) {
    jdbc.update("""
      UPDATE t_task_instance
      SET status='RETRY_WAIT', next_run_time=?, updated_at=now()
      WHERE workflow_instance_id=? AND task_code=? AND attempt=? AND status IN ('PENDING','FAILED','RETRY_WAIT')
    """, Timestamp.from(nextRunTime), workflowInstanceId, taskCode, attempt);
  }

  public void moveToDlq(long workflowInstanceId, long taskInstanceId, String reason) {
    jdbc.update("""
      INSERT INTO t_dlq_task(workflow_instance_id, task_instance_id, reason, payload_json, created_at)
      SELECT workflow_instance_id, task_instance_id, ?, payload_json, now()
      FROM t_task_instance
      WHERE task_instance_id=?
    """, reason, taskInstanceId);

    jdbc.update("""
      UPDATE t_task_instance
      SET status='DLQ', updated_at=now()
      WHERE task_instance_id=? AND workflow_instance_id=?
    """, taskInstanceId, workflowInstanceId);
  }
}
