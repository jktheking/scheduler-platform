package com.acme.scheduler.adapter.jdbc;

import static com.acme.scheduler.common.TimeUtils.toInstant;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.acme.scheduler.service.port.WorkflowInstanceQueryGateway;
import com.acme.scheduler.service.port.WorkflowTrackingQueryGateway; 

/** JDBC adapter for workflow "tracking" / checkpoint queries. */
public final class JdbcWorkflowTrackingQueryGateway implements WorkflowTrackingQueryGateway {

	private final JdbcTemplate jdbc;
	private final WorkflowInstanceQueryGateway instanceGw;

	public JdbcWorkflowTrackingQueryGateway(JdbcTemplate jdbc, WorkflowInstanceQueryGateway instanceGw) {
		this.jdbc = Objects.requireNonNull(jdbc);
		this.instanceGw = Objects.requireNonNull(instanceGw);
	}

	@Override
	public Optional<TrackingView> getTracking(long workflowInstanceId) {
		var instOpt = instanceGw.getInstance(workflowInstanceId);
		if (instOpt.isEmpty())
			return Optional.empty();
		WorkflowInstanceQueryGateway.WorkflowInstance inst = instOpt.get();

		CommandInfo cmd = loadLatestCommand(inst.tenantId(), inst.workflowCode(), inst.workflowVersion());
		List<TriggerInfo> triggers = listTriggers(workflowInstanceId);
		TaskSummary summary = loadTaskSummary(workflowInstanceId);

		return Optional.of(new TrackingView(inst, cmd, triggers, summary));
	}

	private CommandInfo loadLatestCommand(String tenantId, long workflowCode, int workflowVersion) {
		var rows = jdbc.query("""
				SELECT command_id, command_type, created_at, status, last_error_message
				FROM t_command
				WHERE tenant_id=? AND workflow_code=? AND workflow_version=?
				ORDER BY created_at DESC
				LIMIT 1
				""", (rs, i) -> new CommandInfo(rs.getString(1), rs.getString(2), toInstant(rs.getTimestamp(3)),
				rs.getString(4), rs.getString(5)), tenantId, workflowCode, workflowVersion);
		return rows.isEmpty() ? null : rows.get(0);
	}

	private List<TriggerInfo> listTriggers(long workflowInstanceId) {
		return jdbc.query("""
				SELECT trigger_id, due_time, status, claimed_by, claimed_at, updated_at, last_error
				FROM t_trigger
				WHERE workflow_instance_id=?
				ORDER BY due_time DESC
				LIMIT 20
				""",
				(rs, i) -> new TriggerInfo(rs.getLong(1), toInstant(rs.getTimestamp(2)), rs.getString(3),
						rs.getString(4), toInstant(rs.getTimestamp(5)), toInstant(rs.getTimestamp(6)), rs.getString(7)),
				workflowInstanceId);
	}

	private TaskSummary loadTaskSummary(long workflowInstanceId) {
		Map<String, Long> counts = new HashMap<>();
		jdbc.query("""
				SELECT status, COUNT(1)
				FROM t_task_instance
				WHERE workflow_instance_id=?
				GROUP BY status
				""", (RowCallbackHandler) rs -> counts.put(rs.getString(1), rs.getLong(2)), workflowInstanceId);

		Timestamp ts = jdbc.queryForObject("""
				  SELECT MIN(next_run_time)
				  FROM t_task_instance
				  WHERE workflow_instance_id=? AND status='QUEUED'
				""", Timestamp.class, workflowInstanceId);

		Instant oldestQueued = toInstant(ts);

		long total = counts.values().stream().mapToLong(Long::longValue).sum();

		return new TaskSummary(total, counts.getOrDefault("QUEUED", 0L), counts.getOrDefault("CLAIMED", 0L),
				counts.getOrDefault("RUNNING", 0L), counts.getOrDefault("SUCCESS", 0L),
				counts.getOrDefault("FAILED", 0L), counts.getOrDefault("SKIPPED", 0L), oldestQueued);
	}

	
}
