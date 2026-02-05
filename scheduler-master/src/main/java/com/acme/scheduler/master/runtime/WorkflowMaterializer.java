package com.acme.scheduler.master.runtime;

import com.acme.scheduler.domain.dag.Dag;
import com.acme.scheduler.master.adapter.jdbc.JdbcDagTaskInstanceRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcWorkflowDefinitionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Idempotently materializes workflow DAG tasks into t_task_instance.
 */
public final class WorkflowMaterializer {

  private static final Logger log = LoggerFactory.getLogger(WorkflowMaterializer.class);

  public record Materialized(Dag dag, Set<Long> roots, JdbcWorkflowDefinitionRepository.WorkflowDefinitionRow def) {}

  private final JdbcWorkflowDefinitionRepository defs;
  private final JdbcDagTaskInstanceRepository tasks;
  private final ObjectMapper mapper;

  public WorkflowMaterializer(JdbcWorkflowDefinitionRepository defs,
                             JdbcDagTaskInstanceRepository tasks,
                             ObjectMapper mapper) {
    this.defs = Objects.requireNonNull(defs);
    this.tasks = Objects.requireNonNull(tasks);
    this.mapper = Objects.requireNonNull(mapper);
  }

  public Materialized materializeIfAbsent(String tenantId, long workflowCode, int workflowVersion, long workflowInstanceId) {
    if (!tasks.anyForWorkflow(workflowInstanceId)) {
      var defOpt = defs.loadWorkflowDefinition(new JdbcWorkflowDefinitionRepository.WorkflowKey(tenantId, workflowCode, workflowVersion));
      if (defOpt.isEmpty()) {
        throw new IllegalStateException("Workflow definition not found tenant=" + tenantId + " code=" + workflowCode + " v=" + workflowVersion);
      }
      var def = defOpt.get();
      Dag dag = new Dag();
      Set<Long> toNodes = new HashSet<>();

      // Parse tasks from definition_json
      try {
        JsonNode root = mapper.readTree(def.definitionJson());
        JsonNode tasksArr = root.path("tasks");
        if (tasksArr.isArray()) {
          for (JsonNode t : tasksArr) {
            long taskCode = t.path("taskCode").asLong();
            int taskVersion = t.path("taskVersion").asInt(1);
            var tdOpt = defs.loadTaskDefinition(tenantId, taskCode, taskVersion);
            if (tdOpt.isEmpty()) {
              throw new IllegalStateException("Task definition missing tenant=" + tenantId + " task=" + taskCode + " v=" + taskVersion);
            }
            var td = tdOpt.get();
            int maxAttempts = 3;
            try {
              JsonNode tdefJson = mapper.readTree(td.definitionJson());
              maxAttempts = tdefJson.path("maxAttempts").asInt(3);
            } catch (Exception ignored) {}

            dag.addNode(taskCode);
            tasks.insertAttempt0IfAbsent(workflowInstanceId, taskCode, taskVersion, td.name(), td.taskType(), maxAttempts, td.definitionJson());
          }
        }

        JsonNode edgesArr = root.path("edges");
        if (edgesArr.isArray()) {
          for (JsonNode e : edgesArr) {
            long from = e.path("fromTaskCode").asLong();
            long to = e.path("toTaskCode").asLong();
            dag.addEdge(from, to);
            toNodes.add(to);
          }
        } else {
          // Fallback: load edges table
          for (var er : defs.loadEdges(workflowCode, workflowVersion)) {
            dag.addEdge(er.fromTaskCode(), er.toTaskCode());
            toNodes.add(er.toTaskCode());
          }
        }
      } catch (Exception ex) {
        throw new RuntimeException("Invalid workflow definition_json for code=" + workflowCode + " v=" + workflowVersion, ex);
      }

      // Cycle check (throws if cycle)
      dag.topologicalOrder();

      log.info("Materialized workflowInstanceId={} nodes={}", workflowInstanceId, dag.nodes().size());
    }

    // Build dag and roots from edges table (stable even if already materialized)
    var defOpt = defs.loadWorkflowDefinition(new JdbcWorkflowDefinitionRepository.WorkflowKey(tenantId, workflowCode, workflowVersion));
    if (defOpt.isEmpty()) {
      throw new IllegalStateException("Workflow definition not found tenant=" + tenantId + " code=" + workflowCode + " v=" + workflowVersion);
    }
    var def = defOpt.get();

    Dag dag = new Dag();
    Set<Long> toNodes = new HashSet<>();
    try {
      JsonNode root = mapper.readTree(def.definitionJson());
      JsonNode tasksArr = root.path("tasks");
      if (tasksArr.isArray()) {
        for (JsonNode t : tasksArr) {
          long taskCode = t.path("taskCode").asLong();
          dag.addNode(taskCode);
        }
      }
      JsonNode edgesArr = root.path("edges");
      if (edgesArr.isArray()) {
        for (JsonNode e : edgesArr) {
          long from = e.path("fromTaskCode").asLong();
          long to = e.path("toTaskCode").asLong();
          dag.addEdge(from, to);
          toNodes.add(to);
        }
      } else {
        for (var er : defs.loadEdges(workflowCode, workflowVersion)) {
          dag.addEdge(er.fromTaskCode(), er.toTaskCode());
          toNodes.add(er.toTaskCode());
        }
      }
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }

    Set<Long> roots = new LinkedHashSet<>(dag.nodes());
    roots.removeAll(toNodes);
    return new Materialized(dag, roots, def);
  }
}
