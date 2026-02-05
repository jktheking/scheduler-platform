package com.acme.scheduler.worker.adapter.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class JdbcTaskInstanceRepository {

  public record TaskRow(long taskInstanceId, long workflowInstanceId, long taskCode, int attempt, int maxAttempts,
                        String taskType, String payloadJson, String status) {}

  private final JdbcTemplate jdbc;

  public JdbcTaskInstanceRepository(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  public Optional<TaskRow> load(long taskInstanceId) {
    SqlRowSet rs = jdbc.queryForRowSet("""
      SELECT task_instance_id, workflow_instance_id, task_code, attempt, max_attempts, task_type, payload_json::text, status
      FROM t_task_instance
      WHERE task_instance_id=?
    """, taskInstanceId);
    if (!rs.next()) return Optional.empty();
    return Optional.of(new TaskRow(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8)));
  }

  /** Try claim a task idempotently. */
  public boolean tryClaim(long taskInstanceId, int attempt, String workerId) {
    int u = jdbc.update("""
      UPDATE t_task_instance
      SET status='CLAIMED', worker_id=?, claimed_at=now(), updated_at=now()
      WHERE task_instance_id=? AND attempt=? AND status='QUEUED'
    """, workerId, taskInstanceId, attempt);
    return u == 1;
  }

  public void markRunning(long taskInstanceId) {
    jdbc.update("""
      UPDATE t_task_instance
      SET status='RUNNING', started_at=now(), updated_at=now()
      WHERE task_instance_id=? AND status IN ('CLAIMED','RUNNING')
    """, taskInstanceId);
  }

  public void markSuccess(long taskInstanceId) {
    jdbc.update("""
      UPDATE t_task_instance
      SET status='SUCCESS', finished_at=now(), updated_at=now()
      WHERE task_instance_id=? AND status IN ('RUNNING','CLAIMED')
    """, taskInstanceId);
  }

  public void markFailure(long taskInstanceId, String error) {
    jdbc.update("""
      UPDATE t_task_instance
      SET status='FAILED', last_error=?, finished_at=now(), updated_at=now()
      WHERE task_instance_id=? AND status IN ('RUNNING','CLAIMED')
    """, error, taskInstanceId);
  }

  public void markRetryWait(long taskInstanceId, Instant nextRunTime, String error) {
    jdbc.update("""
      UPDATE t_task_instance
      SET status='RETRY_WAIT', last_error=?, next_run_time=?, updated_at=now()
      WHERE task_instance_id=? AND status IN ('FAILED','RUNNING','CLAIMED','RETRY_WAIT')
    """, error, Timestamp.from(nextRunTime), taskInstanceId);
  }

  public void markDlq(long taskInstanceId, String error) {
    jdbc.update("""
      UPDATE t_task_instance
      SET status='DLQ', last_error=?, updated_at=now()
      WHERE task_instance_id=?
    """, error, taskInstanceId);
  }
}
