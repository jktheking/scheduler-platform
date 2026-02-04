package com.acme.scheduler.master.adapter.jdbc;

import com.acme.scheduler.service.workflow.CommandEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * WAL writer: consumes scheduler.commands.v1 and persists to Postgres (t_command).
 * This allows replay, audit, and "direct DB ingestion" parity.
 */
public final class JdbcCommandWriter {

  private final JdbcTemplate jdbc;

  public JdbcCommandWriter(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  /**
   * Inserts if absent. Returns true if inserted, false if duplicate.
   */
  public boolean insertIfAbsent(CommandEnvelope cmd) {
    int updated = jdbc.update("""
      INSERT INTO t_command(
        command_id, tenant_id, idempotency_key, command_type, workflow_code, workflow_version, created_at, payload_json, status
      )
      VALUES (?,?,?,?,?,?,?,?, 'NEW')
      ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
    """,
        cmd.commandId(),
        cmd.tenantId(),
        cmd.idempotencyKey(),
        cmd.commandType(),
        cmd.workflowCode(),
        cmd.workflowVersion(),
        Timestamp.from(cmd.createdAt()),
        cmd.payloadJson()
    );
    return updated == 1;
  }
}
