package com.acme.scheduler.adapter.jdbc;

import com.acme.scheduler.common.TimeUtils;
import com.acme.scheduler.service.port.DlqGateway;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Objects;

/** JDBC adapter for DLQ APIs. */
public final class JdbcDlqGateway implements DlqGateway {

	private final JdbcTemplate jdbc;

	public JdbcDlqGateway(JdbcTemplate jdbc) {
		this.jdbc = Objects.requireNonNull(jdbc);
	}

	@Override
	public List<DlqItem> list(String tenantId, int offset, int limit) {
		// tenant filter is done via join to workflow_instance.
		return jdbc
				.query("""
						SELECT d.dlq_id, d.workflow_instance_id, d.task_instance_id, d.reason, d.created_at
						FROM t_dlq_task d
						JOIN t_workflow_instance wi ON wi.workflow_instance_id = d.workflow_instance_id
						WHERE wi.tenant_id = ?
						ORDER BY d.dlq_id DESC
						OFFSET ? LIMIT ?
						""",
						(rs, rn) -> new DlqItem(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
								TimeUtils.toInstant(rs.getTimestamp(5))),
						tenantId, Math.max(0, offset), Math.max(1, limit));
	}

	@Override
	public void replay(long dlqId) {
		// DLQ replay is a master-owned mutation: it creates new task attempts and schedules triggers.
		// In production, the API publishes a WAL command (REPLAY_DLQ) and only the master leader applies it.
		throw new UnsupportedOperationException(
				"Direct JDBC DLQ replay is disabled. Use the DLQ replay API which publishes a REPLAY_DLQ command to the WAL.");
	}
}
