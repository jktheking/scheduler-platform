package com.acme.scheduler.master.adapter.jdbc;

import com.acme.scheduler.master.port.WorkflowScheduler;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Objects;

public final class JdbcWorkflowScheduler implements WorkflowScheduler {

  private final JdbcTemplate jdbc;

  public JdbcWorkflowScheduler(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  @Override
  public long createAndSchedule(String tenantId, long workflowCode, int workflowVersion, String commandPayloadJson) {
    // v1 minimal: create workflow instance with TRIGGERED status and schedule_time = now
    Long wid = jdbc.queryForObject("""
      INSERT INTO t_workflow_instance(tenant_id, workflow_code, workflow_version, status, created_at, updated_at, schedule_time)
      VALUES (?,?,?, 'TRIGGERED', now(), now(), now())
      RETURNING workflow_instance_id
    """, Long.class, tenantId, workflowCode, workflowVersion);

    long workflowInstanceId = wid == null ? 0L : wid;

    // Persist a minimal execution plan (v1: store command payload as plan_json).
    jdbc.update("""
      INSERT INTO t_workflow_plan(workflow_instance_id, plan_json)
      VALUES (?, ?::jsonb)
      ON CONFLICT (workflow_instance_id) DO NOTHING
    """, workflowInstanceId, commandPayloadJson == null ? "{}" : commandPayloadJson);

    // Schedule trigger (for trigger engine). Minimal: insert due trigger row.
    jdbc.update("""
      INSERT INTO t_trigger(workflow_instance_id, due_time, status)
      VALUES (?, ?, 'DUE')
    """, workflowInstanceId, java.sql.Timestamp.from(Instant.now()));

    return workflowInstanceId;
  }
}
