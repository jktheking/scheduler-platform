package com.acme.scheduler.master.runtime;

import org.springframework.jdbc.core.JdbcTemplate;

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
    Integer c = jdbc.queryForObject("""
      WITH latest AS (
        SELECT task_code, MAX(attempt) AS a
        FROM t_task_instance
        WHERE workflow_instance_id=?
          AND task_code = ANY (?)
        GROUP BY task_code
      )
      SELECT COUNT(1)
      FROM t_task_instance ti
      JOIN latest l ON l.task_code = ti.task_code AND l.a = ti.attempt
      WHERE ti.workflow_instance_id=?
        AND ti.status NOT IN ('SUCCESS','SKIPPED')
    """, Integer.class, workflowInstanceId, arr, workflowInstanceId);
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

  public void markWorkflowSuccess(long workflowInstanceId) {
    jdbc.update("UPDATE t_workflow_instance SET status='SUCCESS', updated_at=now() WHERE workflow_instance_id=?", workflowInstanceId);
  }

  public void markWorkflowFailed(long workflowInstanceId) {
    jdbc.update("UPDATE t_workflow_instance SET status='FAILURE', updated_at=now() WHERE workflow_instance_id=?", workflowInstanceId);
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
