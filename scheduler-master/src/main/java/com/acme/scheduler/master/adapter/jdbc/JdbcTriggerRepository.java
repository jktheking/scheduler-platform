package com.acme.scheduler.master.adapter.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JdbcTriggerRepository {

  public record TriggerRow(long triggerId, long workflowInstanceId, Instant dueTime) {}

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;

  public JdbcTriggerRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.tx = Objects.requireNonNull(tx);
  }

  /**
   * Claim due triggers in a safe concurrent way (multi-master).
   * Uses FOR UPDATE SKIP LOCKED to avoid thundering herd.
   */
  public List<TriggerRow> claimDue(int limit, String claimedBy) {
    return tx.execute(status -> {
      List<TriggerRow> out = new ArrayList<>();
      SqlRowSet rs = jdbc.queryForRowSet("""
        SELECT trigger_id, workflow_instance_id, due_time
        FROM t_trigger
        WHERE status IN ('DUE','ENQUEUED') AND due_time <= now()
        ORDER BY due_time ASC
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      """, limit);
      while (rs.next()) {
        long id = rs.getLong(1);
        long wi = rs.getLong(2);
        Instant due = rs.getTimestamp(3).toInstant();
        jdbc.update("""
          UPDATE t_trigger
          SET status='PROCESSING', claimed_by=?, claimed_at=now(), updated_at=now()
          WHERE trigger_id=?
        """, claimedBy, id);
        out.add(new TriggerRow(id, wi, due));
      }
      return out;
    });
  }

  public void markDone(long triggerId) {
    jdbc.update("""
      UPDATE t_trigger SET status='DONE', updated_at=now() WHERE trigger_id=?
    """, triggerId);
  }

  public void markFailed(long triggerId, String detail) {
    jdbc.update("""
      UPDATE t_trigger SET status='FAILED', last_error=?, updated_at=now() WHERE trigger_id=?
    """, detail, triggerId);
  }

  /**
   * Used by TimeWheel engine: pre-enqueue upcoming triggers into an ENQUEUED state to avoid repeated loads.
   */
  public List<TriggerRow> loadUpcoming(Instant upperBound, int limit) {
    List<TriggerRow> out = new ArrayList<>();
    SqlRowSet rs = jdbc.queryForRowSet("""
      SELECT trigger_id, workflow_instance_id, due_time
      FROM t_trigger
      WHERE status IN ('DUE','ENQUEUED') AND due_time <= ?
      ORDER BY due_time ASC
      LIMIT ?
    """, Timestamp.from(upperBound), limit);
    while (rs.next()) {
      out.add(new TriggerRow(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toInstant()));
    }
    return out;
  }

  public void markEnqueued(long triggerId) {
    jdbc.update("""
      UPDATE t_trigger SET status='ENQUEUED', updated_at=now()
      WHERE trigger_id=? AND status='DUE'
    """, triggerId);
  }

  /** Insert a new due trigger for a workflow instance (used for retries and wakeups). */
  public void scheduleTrigger(long workflowInstanceId, Instant dueTime) {
    jdbc.update("""
      INSERT INTO t_trigger(workflow_instance_id, due_time, status, updated_at)
      VALUES (?, ?, 'DUE', now())
    """, workflowInstanceId, Timestamp.from(dueTime));
  }
}
