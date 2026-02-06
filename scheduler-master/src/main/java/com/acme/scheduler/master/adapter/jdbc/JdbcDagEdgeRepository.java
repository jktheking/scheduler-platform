package com.acme.scheduler.master.adapter.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.util.*;

public final class JdbcDagEdgeRepository {

  private final JdbcTemplate jdbc;

  public JdbcDagEdgeRepository(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc);
  }

  public Map<Long, Set<Long>> outgoing(long workflowCode, int workflowVersion) {
    var m = new HashMap<Long, Set<Long>>();
    jdbc.query("""
      SELECT from_task_code, to_task_code
      FROM t_workflow_dag_edge
      WHERE workflow_code=? AND workflow_version=?
    """, (RowCallbackHandler) rs -> {
      long from = rs.getLong(1);
      long to = rs.getLong(2);
      m.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
    }, workflowCode, workflowVersion);
    return m;
  }

  public Set<Long> incomingOf(long workflowCode, int workflowVersion, long taskCode) {
    var s = new LinkedHashSet<Long>();
    jdbc.query("""
      SELECT from_task_code
      FROM t_workflow_dag_edge
      WHERE workflow_code=? AND workflow_version=? AND to_task_code=?
    """, (RowCallbackHandler) rs -> s.add(rs.getLong(1)),
        workflowCode, workflowVersion, taskCode);
    return s;
  }
}
