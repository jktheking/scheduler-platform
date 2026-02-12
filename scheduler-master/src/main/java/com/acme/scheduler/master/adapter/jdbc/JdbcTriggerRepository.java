package com.acme.scheduler.master.adapter.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.acme.scheduler.master.sharding.ShardCalculator;

public final class JdbcTriggerRepository {

  /** Trigger types persisted in t_trigger.trigger_type. */
  public static final class TriggerTypes {
    private TriggerTypes() {}
    public static final String DAG_WAKE = "DAG_WAKE";
    public static final String WORKFLOW_SLA = "WORKFLOW_SLA";
  }

  public record TriggerRow(long triggerId, long workflowInstanceId, Instant dueTime, String triggerType) {}

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final ShardCalculator shardCalculator;

  public JdbcTriggerRepository(JdbcTemplate jdbc, TransactionTemplate tx, ShardCalculator shardCalculator) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.tx = Objects.requireNonNull(tx);
    this.shardCalculator = Objects.requireNonNull(shardCalculator);
  }

  /**
   * Claim due triggers in a safe concurrent way (multi-master).
   * Uses FOR UPDATE SKIP LOCKED to avoid thundering herd.
   */
  public List<TriggerRow> claimDue(java.util.Set<Integer> shardIds, int limit, String claimedBy) {
    if (shardIds == null || shardIds.isEmpty()) return java.util.List.of();
    return tx.execute(status -> {
      List<TriggerRow> out = new ArrayList<>();

      Integer[] shardIdsArr = shardIds.toArray(Integer[]::new);

      PreparedStatementSetter pss = ps -> {
        java.sql.Array pgArr = ps.getConnection().createArrayOf("integer", shardIdsArr);
        ps.setArray(1, pgArr);
        ps.setInt(2, limit);
      };

      List<TriggerRow> rows = jdbc.query("""
        SELECT trigger_id, workflow_instance_id, due_time, trigger_type
        FROM t_trigger
        WHERE shard_id = ANY (?::int[])
          AND status IN ('DUE','ENQUEUED')
          AND due_time <= now()
        ORDER BY due_time ASC
        LIMIT ?
        FOR UPDATE SKIP LOCKED
      """, pss, (rs, rowNum) -> new TriggerRow(
          rs.getLong(1),
          rs.getLong(2),
          rs.getTimestamp(3).toInstant(),
          rs.getString(4)
      ));

      for (TriggerRow tr : rows) {
        jdbc.update("""
          UPDATE t_trigger
          SET status='PROCESSING', claimed_by=?, claimed_at=now(), updated_at=now()
          WHERE trigger_id=? AND status IN ('DUE','ENQUEUED')
        """, claimedBy, tr.triggerId());
        out.add(tr);
      }

      return out;
    });
  }

  /** Compatibility: claim due triggers across all shards (no shard leadership). */
  public List<TriggerRow> claimDue(int limit, String claimedBy) {
    return tx.execute(status -> {
      List<TriggerRow> out = new ArrayList<>();
      SqlRowSet rs = jdbc.queryForRowSet("""
        SELECT trigger_id, workflow_instance_id, due_time, trigger_type
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
        String type = rs.getString(4);
        jdbc.update("""
          UPDATE t_trigger
          SET status='PROCESSING', claimed_by=?, claimed_at=now(), updated_at=now()
          WHERE trigger_id=?
        """, claimedBy, id);
        out.add(new TriggerRow(id, wi, due, type));
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
      SELECT trigger_id, workflow_instance_id, due_time, trigger_type
      FROM t_trigger
      WHERE status IN ('DUE','ENQUEUED') AND due_time <= ?
      ORDER BY due_time ASC
      LIMIT ?
    """, Timestamp.from(upperBound), limit);
    while (rs.next()) {
      out.add(new TriggerRow(rs.getLong(1), rs.getLong(2), rs.getTimestamp(3).toInstant(), rs.getString(4)));
    }
    return out;
  }

  /** Shard-scoped variant of {@link #loadUpcoming(Instant, int)}. */
  public List<TriggerRow> loadUpcoming(java.util.Set<Integer> shardIds, Instant upperBound, int limit) {
    if (shardIds == null || shardIds.isEmpty()) return java.util.List.of();

    Integer[] shardIdsArr = shardIds.toArray(Integer[]::new);

    PreparedStatementSetter pss = ps -> {
      java.sql.Array pgArr = ps.getConnection().createArrayOf("integer", shardIdsArr);
      ps.setArray(1, pgArr);
      ps.setTimestamp(2, Timestamp.from(upperBound));
      ps.setInt(3, limit);
    };

    return jdbc.query("""
      SELECT trigger_id, workflow_instance_id, due_time, trigger_type
      FROM t_trigger
      WHERE shard_id = ANY (?::int[])
        AND status IN ('DUE','ENQUEUED') AND due_time <= ?
      ORDER BY due_time ASC
      LIMIT ?
    """, pss, (rs, rowNum) -> new TriggerRow(
        rs.getLong(1),
        rs.getLong(2),
        rs.getTimestamp(3).toInstant(),
        rs.getString(4)
    ));
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
      INSERT INTO t_trigger(workflow_instance_id, shard_id, due_time, trigger_type, status, updated_at)
      VALUES (?, ?, ?, ?, 'DUE', now())
    """, workflowInstanceId, shardCalculator.shardForWorkflowInstanceId(workflowInstanceId), Timestamp.from(dueTime), TriggerTypes.DAG_WAKE);
  }

  /** Schedule a workflow SLA trigger (idempotent: at most one per workflow instance). */
  public void scheduleWorkflowSlaTrigger(long workflowInstanceId, Instant dueTime) {
    jdbc.update("""
      INSERT INTO t_trigger(workflow_instance_id, shard_id, due_time, trigger_type, status, updated_at)
      VALUES (?, ?, ?, ?, 'DUE', now())
      ON CONFLICT DO NOTHING
    """, workflowInstanceId, shardCalculator.shardForWorkflowInstanceId(workflowInstanceId), Timestamp.from(dueTime), TriggerTypes.WORKFLOW_SLA);
  }
}
