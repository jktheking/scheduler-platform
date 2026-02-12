package com.acme.scheduler.master.adapter.jdbc;

import com.acme.scheduler.master.ha.ShardLeaderElector;
import com.acme.scheduler.master.port.WorkflowScheduler;
import com.acme.scheduler.master.sharding.ShardCalculator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link WorkflowScheduler}.
 *
 * <p>Architecture note (non-negotiable): workflow_instance_id is allocated by the master
 * when applying START_PROCESS commands. In sharding mode, command routing is by workflow_code
 * (pre-instance), and this scheduler allocates workflow_instance_id values that map to the same
 * shard as the workflow_code to keep shard ownership consistent.
 */
public final class JdbcWorkflowScheduler implements WorkflowScheduler {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final ShardLeaderElector shardElector;
  private final ShardCalculator shardCalculator;
  private final boolean shardingEnabled;

  public JdbcWorkflowScheduler(JdbcTemplate jdbc,
                              ObjectMapper mapper,
                              ShardLeaderElector shardElector,
                              ShardCalculator shardCalculator,
                              boolean shardingEnabled) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.mapper = Objects.requireNonNull(mapper);
    this.shardElector = Objects.requireNonNull(shardElector);
    this.shardCalculator = Objects.requireNonNull(shardCalculator);
    this.shardingEnabled = shardingEnabled;
  }

  @Override
  public long createAndSchedule(String tenantId, long workflowCode, int workflowVersion, String commandPayloadJson) {
    Objects.requireNonNull(tenantId, "tenantId");

    // Defense-in-depth leadership fence.
    // - sharding disabled: single writer fence via leadership for the global shard (0)
    // - sharding enabled: require shard leadership for the workflowCode shard
    int targetShard = shardingEnabled ? shardCalculator.shardForWorkflowCode(workflowCode) : 0;
    if (shardingEnabled) {
      if (!shardElector.isLeader(targetShard)) {
        throw new IllegalStateException("Not shard leader for shard=" + targetShard + ": refusing to createAndSchedule");
      }
    } else {
      if (!shardElector.isLeader(0)) {
        throw new IllegalStateException("Not leader: refusing to createAndSchedule");
      }
    }

    Instant now = Instant.now();
    Instant scheduleTime = parseScheduleTime(commandPayloadJson).orElse(now);
    if (scheduleTime.isBefore(now)) scheduleTime = now;

    long workflowInstanceId = nextWorkflowInstanceIdForShard(targetShard);
    int instanceShard = shardingEnabled ? shardCalculator.shardForWorkflowInstanceId(workflowInstanceId) : 0;

    // 1) Persist workflow instance
    jdbc.update("""
        INSERT INTO t_workflow_instance(
          workflow_instance_id, tenant_id, workflow_code, workflow_version,
          status, created_at, updated_at, schedule_time
        )
        VALUES (?,?,?,?, 'TRIGGERED', now(), now(), ?)
        """,
        workflowInstanceId, tenantId, workflowCode, workflowVersion, Timestamp.from(scheduleTime));

    // 2) Persist execution plan (store command payload as plan_json).
    jdbc.update("""
        INSERT INTO t_workflow_plan(workflow_instance_id, plan_json)
        VALUES (?, ?::jsonb)
        ON CONFLICT (workflow_instance_id) DO NOTHING
        """,
        workflowInstanceId, commandPayloadJson == null ? "{}" : commandPayloadJson);

    // 3) Schedule initial trigger (for trigger engine)
    //    In sharding mode, persist shard_id for efficient shard-scoped scanning.
    jdbc.update("""
        INSERT INTO t_trigger(workflow_instance_id, due_time, status, shard_id)
        VALUES (?, ?, 'DUE', ?)
        """,
        workflowInstanceId, Timestamp.from(scheduleTime), instanceShard);

    return workflowInstanceId;
  }

  private Optional<Instant> parseScheduleTime(String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) return Optional.empty();
    try {
      JsonNode node = mapper.readTree(payloadJson);
      String st = node.path("scheduleTime").asText(null);
      if (st == null || st.isBlank()) return Optional.empty();
      try {
        return Optional.of(Instant.parse(st));
      } catch (DateTimeParseException dtpe) {
        // Accept epoch millis as a fallback
        try {
          long ms = Long.parseLong(st);
          return Optional.of(Instant.ofEpochMilli(ms));
        } catch (Exception ignored) {
          return Optional.empty();
        }
      }
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private long nextWorkflowInstanceIdForShard(int shardId) {
    // Allocate ids until the id lands in the desired shard. This keeps the architecture intact
    // (master allocates workflow_instance_id) while allowing shard-scoped command processing.
    if (!shardingEnabled) {
      Long id = jdbc.queryForObject("SELECT nextval('t_workflow_instance_workflow_instance_id_seq')", Long.class);
      if (id == null) throw new IllegalStateException("nextval returned null");
      return id;
    }

    for (int i = 0; i < 64; i++) { // bounded to avoid infinite loops on misconfig
      Long id = jdbc.queryForObject("SELECT nextval('t_workflow_instance_workflow_instance_id_seq')", Long.class);
      if (id == null) continue;
      if (shardCalculator.shardForWorkflowInstanceId(id) == shardId) return id;
    }
    throw new IllegalStateException("Unable to allocate workflow_instance_id for shard " + shardId +
        " after bounded attempts; check shard count and id generation");
  }
}
