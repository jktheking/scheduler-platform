package com.acme.scheduler.master.runtime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

import java.util.Collection;
import java.util.Objects;

/** Helper queries for workflow instance status checks. */
public final class JdbcTemplateWorkflowInstanceStatus {

  private final JdbcTemplate jdbc;

  public JdbcTemplateWorkflowInstanceStatus(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  public int countNotSuccessfulParents(long workflowInstanceId, Collection<Long> parentTaskCodes) {
    if (parentTaskCodes == null || parentTaskCodes.isEmpty()) return 0;

    Long[] arr = parentTaskCodes.toArray(Long[]::new);

    PreparedStatementSetter pss = ps -> {
      java.sql.Array pgArr = ps.getConnection().createArrayOf("bigint", arr);
      ps.setLong(1, workflowInstanceId);
      ps.setArray(2, pgArr);
      ps.setLong(3, workflowInstanceId);
    };

    Integer c = jdbc.query("""
      WITH latest AS (
        SELECT task_code, MAX(attempt) AS a
        FROM t_task_instance
        WHERE workflow_instance_id=?
          AND task_code = ANY (?::bigint[])
        GROUP BY task_code
      )
      SELECT COUNT(1)
      FROM t_task_instance ti
      JOIN latest l ON l.task_code = ti.task_code AND l.a = ti.attempt
      WHERE ti.workflow_instance_id=?
        AND ti.status NOT IN ('SUCCESS','SKIPPED')
    """, pss, rs -> rs.next() ? rs.getInt(1) : 0);

    return c == null ? 0 : c;
  }

  public boolean allAttempt0TerminalSuccess(long workflowInstanceId) {
    Integer c = jdbc.queryForObject("""
      WITH latest AS (
        SELECT task_code, MAX(attempt) AS a
        FROM t_task_instance
        WHERE workflow_instance_id=?
        GROUP BY task_code
      )
      SELECT COUNT(1)
      FROM t_task_instance ti
      JOIN latest l ON l.task_code = ti.task_code AND l.a = ti.attempt
      WHERE ti.workflow_instance_id=?
        AND ti.status NOT IN ('SUCCESS','SKIPPED')
    """, Integer.class, workflowInstanceId, workflowInstanceId);
    return c != null && c == 0;
  }

  public void markWorkflowRunning(long workflowInstanceId) {
    jdbc.update("UPDATE t_workflow_instance SET status='RUNNING', updated_at=now() WHERE workflow_instance_id=? AND status IN ('TRIGGERED','CREATED')", workflowInstanceId);
  }

  /**
   * @return true iff the workflow transitioned into RUNNING in this call.
   */
  public boolean markWorkflowRunningIfStart(long workflowInstanceId) {
    int updated = jdbc.update("UPDATE t_workflow_instance SET status='RUNNING', updated_at=now() WHERE workflow_instance_id=? AND status IN ('TRIGGERED','CREATED')", workflowInstanceId);
    return updated == 1;
  }

  public String loadStatus(long workflowInstanceId) {
    return jdbc.queryForObject("SELECT status FROM t_workflow_instance WHERE workflow_instance_id=?", String.class, workflowInstanceId);
  }

  public boolean isTerminal(long workflowInstanceId) {
    String s = loadStatus(workflowInstanceId);
    return s != null && (s.equals("SUCCESS") || s.equals("FAILURE"));
  }

  public void markWorkflowSuccess(long workflowInstanceId) {
    jdbc.update("UPDATE t_workflow_instance SET status='SUCCESS', updated_at=now() WHERE workflow_instance_id=?", workflowInstanceId);
  }

  public void markWorkflowFailed(long workflowInstanceId) {
    markWorkflowFailed(workflowInstanceId, null);
  }

  public void markWorkflowFailed(long workflowInstanceId, String lastError) {
    jdbc.update("UPDATE t_workflow_instance SET status='FAILURE', last_error=?, updated_at=now() WHERE workflow_instance_id=?", lastError, workflowInstanceId);
  }

  /**
   * Mark workflow as FAILURE only if it is not already in a terminal state.
   *
   * @return true iff the status transitioned to FAILURE.
   */
  public boolean markWorkflowFailedIfNotTerminal(long workflowInstanceId, String lastError) {
    int updated = jdbc.update("""
        UPDATE t_workflow_instance
        SET status='FAILURE', last_error=?, updated_at=now()
        WHERE workflow_instance_id=?
          AND status NOT IN ('SUCCESS','FAILURE')
        """, lastError, workflowInstanceId);
    return updated == 1;
  }

  public record InstanceKey(String tenantId, long workflowCode, int workflowVersion) {}

  public InstanceKey loadInstanceKey(long workflowInstanceId) {
    return jdbc.queryForObject("""
      SELECT tenant_id, workflow_code, workflow_version
      FROM t_workflow_instance
      WHERE workflow_instance_id=?
    """, (rs, rowNum) -> new InstanceKey(rs.getString(1), rs.getLong(2), rs.getInt(3)), workflowInstanceId);
  }
}
