package com.acme.scheduler.adapter.jdbc;

import com.acme.scheduler.service.port.WorkflowDefinitionGateway;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** JDBC adapter for workflow/task definitions. */
public final class JdbcWorkflowDefinitionGateway implements WorkflowDefinitionGateway {

  private final JdbcTemplate jdbc;

  public JdbcWorkflowDefinitionGateway(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  @Override
  public long allocateWorkflowCode() {
    Long v = jdbc.queryForObject("SELECT nextval('seq_workflow_code')", Long.class);
    return v == null ? 0L : v;
  }

  @Override
  public long allocateTaskCode() {
    Long v = jdbc.queryForObject("SELECT nextval('seq_task_code')", Long.class);
    return v == null ? 0L : v;
  }

  @Override
  public UpsertResult upsertWorkflowDefinition(String tenantId,
                                              Long workflowCodeOrNull,
                                              String name,
                                              List<TaskSpec> tasks,
                                              List<EdgeSpec> edges) {
    long workflowCode = (workflowCodeOrNull == null) ? allocateWorkflowCode() : workflowCodeOrNull;
    Integer maxVer = jdbc.queryForObject(
        "SELECT COALESCE(MAX(workflow_version),0) FROM t_workflow_definition WHERE workflow_code=?",
        Integer.class,
        workflowCode);
    int workflowVersion = (maxVer == null ? 0 : maxVer) + 1;

    // Upsert task definitions first.
    for (TaskSpec t : tasks) {
      jdbc.update(
          """
          INSERT INTO t_task_definition(task_code, task_version, tenant_id, name, task_type, definition_json)
          VALUES(?,?,?,?,?,?::jsonb)
          ON CONFLICT(task_code, task_version)
          DO UPDATE SET tenant_id=EXCLUDED.tenant_id, name=EXCLUDED.name, task_type=EXCLUDED.task_type, definition_json=EXCLUDED.definition_json
          """,
          t.taskCode(), t.taskVersion(), tenantId, t.name(), t.taskType(), t.definitionJson());
    }

    // Store workflow definition JSON for the master materializer.
    String definitionJson = buildDefinitionJson(tasks, edges);

    jdbc.update(
        """
        INSERT INTO t_workflow_definition(workflow_code, workflow_version, tenant_id, name, definition_json)
        VALUES(?,?,?,?,?::jsonb)
        ON CONFLICT(workflow_code, workflow_version)
        DO UPDATE SET tenant_id=EXCLUDED.tenant_id, name=EXCLUDED.name, definition_json=EXCLUDED.definition_json
        """,
        workflowCode, workflowVersion, tenantId, name, definitionJson);

    // Replace edges table for this version.
    jdbc.update("DELETE FROM t_workflow_dag_edge WHERE workflow_code=? AND workflow_version=?", workflowCode, workflowVersion);
    for (EdgeSpec e : edges) {
      jdbc.update(
          "INSERT INTO t_workflow_dag_edge(workflow_code, workflow_version, from_task_code, to_task_code) VALUES(?,?,?,?)",
          workflowCode, workflowVersion, e.fromTaskCode(), e.toTaskCode());
    }

    return new UpsertResult(workflowCode, workflowVersion);
  }

  @Override
  public List<WorkflowSummary> listLatest(String tenantId, int limit) {
    int lim = Math.max(1, limit);
    // Latest version per workflow_code.
    return jdbc.query(
        """
        SELECT DISTINCT ON (workflow_code)
               tenant_id, workflow_code, workflow_version, name
        FROM t_workflow_definition
        WHERE tenant_id=?
        ORDER BY workflow_code, workflow_version DESC
        LIMIT ?
        """,
        (rs, i) -> new WorkflowSummary(rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getString(4)),
        tenantId,
        lim);
  }

  private static String jsonEscape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  /**
   * Minimal JSON builder to avoid forcing core to depend on Jackson.
   * Adapter layer is allowed to build strings.
   */
  private static String buildDefinitionJson(List<TaskSpec> tasks, List<EdgeSpec> edges) {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    sb.append("\"tasks\":[");
    for (int i = 0; i < tasks.size(); i++) {
      TaskSpec t = tasks.get(i);
      if (i > 0) sb.append(',');
      sb.append('{')
          .append("\"taskCode\":").append(t.taskCode()).append(',')
          .append("\"taskVersion\":").append(t.taskVersion()).append(',')
          .append("\"name\":\"").append(jsonEscape(t.name())).append("\",")
          .append("\"taskType\":\"").append(jsonEscape(t.taskType())).append("\",")
          .append("\"definition\":").append(t.definitionJson() == null ? "{}" : t.definitionJson())
          .append('}');
    }
    sb.append(']');
    sb.append(",\"edges\":[");
    for (int i = 0; i < edges.size(); i++) {
      EdgeSpec e = edges.get(i);
      if (i > 0) sb.append(',');
      sb.append('{')
          .append("\"fromTaskCode\":").append(e.fromTaskCode()).append(',')
          .append("\"toTaskCode\":").append(e.toTaskCode())
          .append('}');
    }
    sb.append(']');
    sb.append('}');
    return sb.toString();
  }
}
