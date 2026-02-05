package com.acme.scheduler.master.adapter.jdbc;

import com.acme.scheduler.master.port.WorkflowScheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public final class JdbcWorkflowScheduler implements WorkflowScheduler {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcWorkflowScheduler(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.mapper = Objects.requireNonNull(mapper);
  }

  @Override
  public long createAndSchedule(String tenantId, long workflowCode, int workflowVersion, String commandPayloadJson) {
    Instant now = Instant.now();
    Instant scheduleTime = parseScheduleTime(commandPayloadJson).orElse(now);
    if (scheduleTime.isBefore(now)) scheduleTime = now;

    Long wid = jdbc.queryForObject("""
      INSERT INTO t_workflow_instance(tenant_id, workflow_code, workflow_version, status, created_at, updated_at, schedule_time)
      VALUES (?,?,?, 'TRIGGERED', now(), now(), ?)
      RETURNING workflow_instance_id
    """, Long.class, tenantId, workflowCode, workflowVersion, Timestamp.from(scheduleTime));

    long workflowInstanceId = wid == null ? 0L : wid;

    // Persist execution plan (store command payload as plan_json).
    jdbc.update("""
      INSERT INTO t_workflow_plan(workflow_instance_id, plan_json)
      VALUES (?, ?::jsonb)
      ON CONFLICT (workflow_instance_id) DO NOTHING
    """, workflowInstanceId, commandPayloadJson == null ? "{}" : commandPayloadJson);

    // Schedule trigger (for trigger engine).
    jdbc.update("""
      INSERT INTO t_trigger(workflow_instance_id, due_time, status)
      VALUES (?, ?, 'DUE')
    """, workflowInstanceId, Timestamp.from(scheduleTime));

    return workflowInstanceId;
  }

  private java.util.Optional<Instant> parseScheduleTime(String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) return java.util.Optional.empty();
    try {
      JsonNode node = mapper.readTree(payloadJson);
      String st = node.path("scheduleTime").asText(null);
      if (st == null || st.isBlank()) return java.util.Optional.empty();
      try {
        return java.util.Optional.of(Instant.parse(st));
      } catch (DateTimeParseException dtpe) {
        // Accept epoch millis as a fallback
        try {
          long ms = Long.parseLong(st);
          return java.util.Optional.of(Instant.ofEpochMilli(ms));
        } catch (Exception ignored) {
          return java.util.Optional.empty();
        }
      }
    } catch (Exception e) {
      return java.util.Optional.empty();
    }
  }
}
