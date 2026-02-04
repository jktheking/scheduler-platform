package com.acme.scheduler.master.adapter.jdbc;

import com.acme.scheduler.domain.execution.CommandType;
import com.acme.scheduler.master.port.CommandQueue;
import com.acme.scheduler.master.runtime.CommandRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.util.List;

public final class JdbcCommandQueue implements CommandQueue {

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;

  public JdbcCommandQueue(JdbcTemplate jdbc, TransactionTemplate tx) {
    this.jdbc = jdbc;
    this.tx = tx;
  }

  @Override
  public List<CommandRecord> claimBatch(int limit) {
    return tx.execute(status -> {
      // Claim NEW commands using SKIP LOCKED to allow multiple masters
      List<CommandRecord> rows = jdbc.query("""
        SELECT command_id, tenant_id, command_type, workflow_code, workflow_version, payload_json, idempotency_key, created_at
        FROM t_command
        WHERE status='NEW'
        ORDER BY created_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT ?
      """, (rs, rn) -> map(rs), limit);

      if (rows.isEmpty()) return rows;

      // Mark as PROCESSING
      String inClause = rows.stream().map(r -> "?").reduce((a,b)->a+","+b).orElse("?");
      Object[] args = rows.stream().map(CommandRecord::commandId).toArray();
      jdbc.update("UPDATE t_command SET status='PROCESSING' WHERE command_id IN (" + inClause + ")", args);

      return rows;
    });
  }

  @Override
  public void markDone(String commandId) {
    jdbc.update("""
      UPDATE t_command SET status='DONE', last_error_message=NULL WHERE command_id=?
    """, commandId);
  }

  @Override
  public void markFailed(String commandId, String error) {
    jdbc.update("""
      UPDATE t_command SET status='FAILED', last_error_message=? WHERE command_id=?
    """, error, commandId);
  }

  private static CommandRecord map(ResultSet rs) throws java.sql.SQLException {
    return new CommandRecord(
        rs.getString("command_id"),
        rs.getString("tenant_id"),
        CommandType.valueOf(rs.getString("command_type")),
        rs.getLong("workflow_code"),
        rs.getInt("workflow_version"),
        rs.getString("payload_json"),
        rs.getString("idempotency_key"),
        rs.getTimestamp("created_at").toInstant()
    );
  }
}
