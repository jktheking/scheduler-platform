package com.acme.scheduler.domain.dag;

import com.acme.scheduler.domain.DagCycleException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DagTest {

 @Test
 void topoSort_ordersDependencies() {
 Dag dag = new Dag();
 dag.addEdge(1, 2);
 dag.addEdge(1, 3);
 dag.addEdge(2, 4);
 dag.addEdge(3, 4);

 List<Long> order = dag.topologicalOrder();
 assertTrue(order.indexOf(1L) < order.indexOf(2L));
 assertTrue(order.indexOf(1L) < order.indexOf(3L));
 assertTrue(order.indexOf(2L) < order.indexOf(4L));
 assertTrue(order.indexOf(3L) < order.indexOf(4L));
 }

 @Test
 void detectsCycle() {
 Dag dag = new Dag();
 dag.addEdge(1, 2);
 dag.addEdge(2, 3);
 dag.addEdge(3, 1);
 assertThrows(DagCycleException.class, dag::topologicalOrder);
 }
}
