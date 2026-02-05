package com.acme.scheduler.service.workflowdef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.acme.scheduler.service.port.WorkflowDefinitionGateway;
import com.fasterxml.jackson.databind.JsonNode;

/** Application usecase: create a new workflow definition (or a new version) from JSON arrays. */
public final class UpsertWorkflowDefinitionUseCase {

  private final WorkflowDefinitionGateway gw;

  public UpsertWorkflowDefinitionUseCase(WorkflowDefinitionGateway gw) {
    this.gw = Objects.requireNonNull(gw);
  }

  public record Response(long workflowCode, int workflowVersion) {}

  /**
   * @param tasksJson JSON array of tasks: [{"name":"A","taskType":"SCRIPT","definition":{...}, "taskCode"?:123, "taskVersion"?:1}]
   * @param edgesJson JSON array of edges: [{"from":"A","to":"B"}] or [{"fromTaskCode":..,"toTaskCode":..}]
   */
  public Response handle(String tenantId,
                         String name,
                         JsonNode tasksJson,
                         JsonNode edgesJson,
                         Long workflowCodeOrNull) {
    if (tenantId == null || tenantId.isBlank()) tenantId = "default";
    if (name == null || name.isBlank()) name = "workflow";

    List<WorkflowDefinitionGateway.TaskSpec> tasks = new ArrayList<>();
    Map<String, Long> nameToCode = new HashMap<>();

    if (tasksJson != null && tasksJson.isArray()) {
      for (JsonNode t : tasksJson) {
        String taskName = text(t, "name", "task");
        String taskType = normalizeType(text(t, "taskType", "SCRIPT"));
        long taskCode = longOrZero(t, "taskCode");
        if (taskCode == 0L) taskCode = gw.allocateTaskCode();
        int taskVersion = intOrDefault(t, "taskVersion", 1);
        JsonNode def = t.get("definition");
        String defJson = (def == null) ? "{}" : def.toString();

        tasks.add(new WorkflowDefinitionGateway.TaskSpec(taskCode, taskVersion, taskName, taskType, defJson));
        nameToCode.put(taskName, taskCode);
      }
    }

    List<WorkflowDefinitionGateway.EdgeSpec> edges = new ArrayList<>();
    if (edgesJson != null && edgesJson.isArray()) {
      for (JsonNode e : edgesJson) {
        long from = longOrZero(e, "fromTaskCode");
        long to = longOrZero(e, "toTaskCode");
        if (from == 0L || to == 0L) {
          String fromName = text(e, "from", null);
          String toName = text(e, "to", null);
          if (fromName != null) from = nameToCode.getOrDefault(fromName, 0L);
          if (toName != null) to = nameToCode.getOrDefault(toName, 0L);
        }
        if (from != 0L && to != 0L) {
          edges.add(new WorkflowDefinitionGateway.EdgeSpec(from, to));
        }
      }
    }

    WorkflowDefinitionGateway.UpsertResult r = gw.upsertWorkflowDefinition(
        tenantId,
        workflowCodeOrNull,
        name,
        tasks,
        edges
    );
    return new Response(r.workflowCode(), r.workflowVersion());
  }

  private static String normalizeType(String raw) {
    if (raw == null) return "SCRIPT";
    String u = raw.trim().toUpperCase(Locale.ROOT);
    return (u.equals("HTTP") || u.equals("SCRIPT")) ? u : "SCRIPT";
  }

  private static String text(JsonNode n, String field, String def) {
    if (n == null) return def;
    JsonNode v = n.get(field);
    return (v == null || v.isNull()) ? def : v.asText();
  }

  private static long longOrZero(JsonNode n, String field) {
    if (n == null) return 0L;
    JsonNode v = n.get(field);
    return (v == null || v.isNull()) ? 0L : v.asLong();
  }

  private static int intOrDefault(JsonNode n, String field, int def) {
    if (n == null) return def;
    JsonNode v = n.get(field);
    return (v == null || v.isNull()) ? def : v.asInt(def);
  }
}
