package com.acme.scheduler.adapter.jdbc;

import java.sql.Timestamp;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.acme.scheduler.service.port.CommandRepository;
import com.acme.scheduler.service.workflow.CommandEnvelope;

/**
 * JDBC command repository (Postgres default).
 *
 * Idempotency is enforced by a UNIQUE constraint on (tenant_id, idempotency_key).
 */
public final class JdbcCommandRepository implements CommandRepository {

  private final JdbcTemplate jdbc;

  public JdbcCommandRepository(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  @Override
  public InsertResult insertIfAbsent(CommandEnvelope command) {
    try {
      int updated = jdbc.update(
          """
          INSERT INTO t_command(
            tenant_id, command_id, idempotency_key, command_type,
            workflow_code, workflow_version, created_at, payload_json, status
          )
          VALUES (?,?,?,?,?,?,?,?::jsonb, 'NEW')
          """,
          command.tenantId(),
          command.commandId(),
          command.idempotencyKey(),
          command.commandType(),
          command.workflowCode(),
          command.workflowVersion(),
          Timestamp.from(command.createdAt()),
          command.payloadJson()
      );
      return updated == 1 ? InsertResult.INSERTED : InsertResult.DUPLICATE;
    } catch (DuplicateKeyException dup) {
      return InsertResult.DUPLICATE;
    }
  }
}
