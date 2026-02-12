package com.acme.scheduler.master.adapter.jdbc;

import com.acme.scheduler.master.port.DlqReplayer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** JDBC implementation of master-owned DLQ replay. */
public final class JdbcDlqReplayer implements DlqReplayer {

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;

  public JdbcDlqReplayer(JdbcTemplate jdbc, TransactionTemplate tx) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.tx = Objects.requireNonNull(tx);
  }

  @Override
  public void replay(long dlqId) {
    tx.executeWithoutResult(status -> {
      List<java.util.Map<String, Object>> rows = jdbc.queryForList(
          "SELECT workflow_instance_id, task_instance_id FROM t_dlq_task WHERE dlq_id=?",
          dlqId);
      if (rows.isEmpty()) return;

      long workflowInstanceId = ((Number) rows.getFirst().get("workflow_instance_id")).longValue();
      long dlqTaskInstanceId = ((Number) rows.getFirst().get("task_instance_id")).longValue();

      var rs = jdbc.queryForRowSet("""
          SELECT task_code, task_version, task_name, task_type, attempt, max_attempts, payload_json::text
          FROM t_task_instance
          WHERE task_instance_id=?
          """, dlqTaskInstanceId);
      if (!rs.next()) return;

      long taskCode = rs.getLong(1);
      int taskVersion = rs.getInt(2);
      String taskName = rs.getString(3);
      String taskType = rs.getString(4);
      int attempt = rs.getInt(5);
      int maxAttempts = rs.getInt(6);
      String payloadJson = rs.getString(7);

      int newAttempt = attempt + 1;
      int newMaxAttempts = Math.max(maxAttempts, newAttempt + 1);

      // Create a new attempt row and set it to RETRY_WAIT + next_run_time=now.
      jdbc.update("""
          INSERT INTO t_task_instance(
            workflow_instance_id, task_code, task_version, task_name, task_type,
            status, attempt, max_attempts, next_run_time, payload_json, created_at, updated_at
          )
          VALUES (?, ?, ?, ?, ?, 'RETRY_WAIT', ?, ?, ?, ?::jsonb, now(), now())
          ON CONFLICT (workflow_instance_id, task_code, attempt) DO NOTHING
          """,
          workflowInstanceId, taskCode, taskVersion, taskName, taskType,
          newAttempt, newMaxAttempts,
          Timestamp.from(Instant.now()), payloadJson == null ? "{}" : payloadJson);

      // Mark old DLQ attempt CANCELLED (keep DLQ row for audit).
      jdbc.update(
          "UPDATE t_task_instance SET status='CANCELLED', updated_at=now() WHERE task_instance_id=? AND status='DLQ'",
          dlqTaskInstanceId);

      // If workflow is FAILURE, move it back to RUNNING.
      jdbc.update(
          "UPDATE t_workflow_instance SET status='RUNNING', updated_at=now() WHERE workflow_instance_id=? AND status='FAILURE'",
          workflowInstanceId);

      // Schedule a wakeup trigger so master publishes the retry attempt.
      jdbc.update("""
          INSERT INTO t_trigger(workflow_instance_id, due_time, status)
          VALUES (?, ?, 'DUE')
          """,
          workflowInstanceId, Timestamp.from(Instant.now()));
    });
  }
}
