package com.acme.scheduler.master.adapter.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

/**
 * Protects master processing against Kafka at-least-once delivery.
 * Uses t_command_dedupe as a cheap guard: insert-once per command_id.
 */
public final class JdbcCommandDedupeRepository {

  private final JdbcTemplate jdbc;

  public JdbcCommandDedupeRepository(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  /**
   * @return true if this command was not processed before (insert succeeded).
   */
  public boolean tryMarkProcessing(String commandId) {
    int updated = jdbc.update("""
      INSERT INTO t_command_dedupe(command_id, outcome)
      VALUES (?, 'PROCESSING')
      ON CONFLICT (command_id) DO NOTHING
    """, commandId);
    return updated == 1;
  }

  public void markOutcome(String commandId, String outcome, String detail) {
    jdbc.update("""
      UPDATE t_command_dedupe
      SET processed_at = now(), outcome = ?, detail = ?
      WHERE command_id = ?
    """, outcome, detail, commandId);
  }
}
