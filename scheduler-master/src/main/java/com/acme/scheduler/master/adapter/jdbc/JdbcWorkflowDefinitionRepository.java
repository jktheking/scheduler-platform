package com.acme.scheduler.master.adapter.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.util.*;

/** Reads workflow definition from definition tables. */
public final class JdbcWorkflowDefinitionRepository {

  public record WorkflowKey(String tenantId, long workflowCode, int workflowVersion) {}

  public record WorkflowDefinitionRow(long workflowCode, int workflowVersion, String tenantId, String name, String definitionJson) {}

  public record TaskDefinitionRow(long taskCode, int taskVersion, String tenantId, String name, String taskType, String definitionJson) {}

  public record EdgeRow(long fromTaskCode, long toTaskCode) {}

  private final JdbcTemplate jdbc;

  public JdbcWorkflowDefinitionRepository(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  public Optional<WorkflowDefinitionRow> loadWorkflowDefinition(WorkflowKey key) {
    SqlRowSet rs = jdbc.queryForRowSet("""
      SELECT workflow_code, workflow_version, tenant_id, name, definition_json::text
      FROM t_workflow_definition
      WHERE tenant_id=? AND workflow_code=? AND workflow_version=?
    """, key.tenantId(), key.workflowCode(), key.workflowVersion());
    if (!rs.next()) return Optional.empty();
    return Optional.of(new WorkflowDefinitionRow(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5)));
  }

  public List<TaskDefinitionRow> loadTaskDefinitionsForWorkflow(long workflowCode, int workflowVersion, String tenantId) {
    // For v2 minimal, task_definition table doesn't have workflow FK. We'll expect workflow definition_json to contain task codes.
    // This method is unused by default materializer.
    return List.of();
  }

  public Optional<TaskDefinitionRow> loadTaskDefinition(String tenantId, long taskCode, int taskVersion) {
    SqlRowSet rs = jdbc.queryForRowSet("""
      SELECT task_code, task_version, tenant_id, name, task_type, definition_json::text
      FROM t_task_definition
      WHERE tenant_id=? AND task_code=? AND task_version=?
    """, tenantId, taskCode, taskVersion);
    if (!rs.next()) return Optional.empty();
    return Optional.of(new TaskDefinitionRow(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
  }

  public List<EdgeRow> loadEdges(long workflowCode, int workflowVersion) {
    List<EdgeRow> out = new ArrayList<>();
    SqlRowSet rs = jdbc.queryForRowSet("""
      SELECT from_task_code, to_task_code
      FROM t_workflow_dag_edge
      WHERE workflow_code=? AND workflow_version=?
    """, workflowCode, workflowVersion);
    while (rs.next()) out.add(new EdgeRow(rs.getLong(1), rs.getLong(2)));
    return out;
  }
}
