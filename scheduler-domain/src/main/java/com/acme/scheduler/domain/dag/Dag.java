package com.acme.scheduler.domain.dag;

import com.acme.scheduler.domain.DagCycleException;

import java.util.*;

/**
 * Simple DAG structure used by orchestration planning (topological order, cycle
 * detection). Nodes are represented by long codes (workflow tasks).
 */
public final class Dag {
	private final Map<Long, Set<Long>> outgoing = new HashMap<>();
	private final Map<Long, Set<Long>> incoming = new HashMap<>();

	public void addNode(long node) {
	
		outgoing.computeIfAbsent(node, ignored  -> new LinkedHashSet<>());
		incoming.computeIfAbsent(node, ignored  -> new LinkedHashSet<>());
	}

	public void addEdge(long from, long to) {
		addNode(from);
		addNode(to);
		if (from == to)
			throw new DagCycleException("Self-cycle detected for node " + from);
		outgoing.get(from).add(to);
		incoming.get(to).add(from);
	}

	public Set<Long> nodes() {
		Set<Long> all = new LinkedHashSet<>();
		all.addAll(outgoing.keySet());
		all.addAll(incoming.keySet());
		return Collections.unmodifiableSet(all);
	}

	public List<Long> topologicalOrder() {
		Map<Long, Integer> indegree = new HashMap<>();
		for (long n : nodes()) {
			indegree.put(n, incoming.getOrDefault(n, Set.of()).size());
		}

		ArrayDeque<Long> q = new ArrayDeque<>();
		for (var e : indegree.entrySet()) {
			if (e.getValue() == 0)
				q.add(e.getKey());
		}

		List<Long> order = new ArrayList<>(nodes().size());
		while (!q.isEmpty()) {
			long n = q.removeFirst();
			order.add(n);
			for (long m : outgoing.getOrDefault(n, Set.of())) {
				int d = indegree.computeIfPresent(m, (k, v) -> v - 1);
				if (d == 0)
					q.add(m);
			}
		}

		if (order.size() != nodes().size()) {
			throw new DagCycleException(
					"Cycle detected: topo order size " + order.size() + " != nodes " + nodes().size());
		}
		return order;
	}
}
