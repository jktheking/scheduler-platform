package com.acme.scheduler.adapter.jdbc;

import com.acme.scheduler.service.port.WorkflowInstanceQueryGateway;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** JDBC adapter for runtime instance queries. */
public final class JdbcWorkflowInstanceQueryGateway implements WorkflowInstanceQueryGateway {

  private final JdbcTemplate jdbc;

  public JdbcWorkflowInstanceQueryGateway(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  @Override
  public Optional<WorkflowInstance> getInstance(long workflowInstanceId) {
    var rows = jdbc.query(
        """
        SELECT workflow_instance_id, tenant_id, workflow_code, workflow_version, status, created_at, updated_at, schedule_time, last_error
        FROM t_workflow_instance
        WHERE workflow_instance_id=?
        """,
        (rs, i) -> new WorkflowInstance(
            rs.getLong(1),
            rs.getString(2),
            rs.getLong(3),
            rs.getInt(4),
            rs.getString(5),
            toInstant(rs.getTimestamp(6)),
            toInstant(rs.getTimestamp(7)),
            toInstant(rs.getTimestamp(8)),
            rs.getString(9)
        ),
        workflowInstanceId);
    return rows.stream().findFirst();
  }

  @Override
  public List<TaskInstance> listTasks(long workflowInstanceId) {
    return jdbc.query(
        """
        SELECT task_instance_id, workflow_instance_id, task_code, task_version, task_name, task_type,
               status, attempt, max_attempts, next_run_time, worker_id, claimed_at, started_at, finished_at, last_error
        FROM t_task_instance
        WHERE workflow_instance_id=?
        ORDER BY task_instance_id ASC
        """,
        (rs, i) -> new TaskInstance(
            rs.getLong(1),
            rs.getLong(2),
            rs.getLong(3),
            rs.getInt(4),
            rs.getString(5),
            rs.getString(6),
            rs.getString(7),
            rs.getInt(8),
            rs.getInt(9),
            toInstant(rs.getTimestamp(10)),
            rs.getString(11),
            toInstant(rs.getTimestamp(12)),
            toInstant(rs.getTimestamp(13)),
            toInstant(rs.getTimestamp(14)),
            rs.getString(15)
        ),
        workflowInstanceId);
  }


  @Override
  public List<TriggerRow> listRecentTriggers(long workflowInstanceId, int limit) {
    return jdbc.query(
        """
        SELECT trigger_id, workflow_instance_id, due_time, status, claimed_by, claimed_at, updated_at, last_error
        FROM t_trigger
        WHERE workflow_instance_id=?
        ORDER BY trigger_id DESC
        LIMIT ?
        """,
        (rs, i) -> new TriggerRow(
            rs.getLong(1),
            rs.getLong(2),
            toInstant(rs.getTimestamp(3)),
            rs.getString(4),
            rs.getString(5),
            toInstant(rs.getTimestamp(6)),
            toInstant(rs.getTimestamp(7)),
            rs.getString(8)
        ),
        workflowInstanceId, limit);
  }

  @Override
  public Optional<CommandRow> getLatestCommand(String tenantId, long workflowCode, int workflowVersion) {
    var rows = jdbc.query(
        """
        SELECT command_id, tenant_id, idempotency_key, command_type, workflow_code, workflow_version, created_at, status, last_error_message
        FROM t_command
        WHERE tenant_id=? AND workflow_code=? AND workflow_version=?
        ORDER BY created_at DESC
        LIMIT 1
        """,
        (rs, i) -> new CommandRow(
            rs.getString(1),
            rs.getString(2),
            rs.getString(3),
            rs.getString(4),
            rs.getLong(5),
            rs.getInt(6),
            toInstant(rs.getTimestamp(7)),
            rs.getString(8),
            rs.getString(9)
        ),
        tenantId, workflowCode, workflowVersion);
    return rows.stream().findFirst();
  }

  private static Instant toInstant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
