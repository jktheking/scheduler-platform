package com.acme.scheduler.service.bus;

import com.acme.scheduler.meter.SchedulerMeter;
import com.acme.scheduler.service.port.CommandBus;
import com.acme.scheduler.service.workflow.CommandEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class MeteredCommandBusTest {

  @Test
  void publish_incrementsSuccessCounter() {
    AtomicLong ok = new AtomicLong();
    AtomicLong fail = new AtomicLong();

    SchedulerMeter meter = new SchedulerMeter() {
      @Override public Counter counter(String name, String description) {
        if (name.contains("failed")) {
          return (v, labels) -> fail.addAndGet(v);
        }
        return (v, labels) -> ok.addAndGet(v);
      }
      @Override public Histogram histogram(String name, String description, String unit) { return (v, labels) -> {}; }
    };

    CommandBus delegate = command -> { /* ok */ };
    MeteredCommandBus bus = new MeteredCommandBus(delegate, meter);

    bus.publish(new CommandEnvelope(
        "t1", "cmd-1", "idem-1", "StartWorkflow", 123L, 1, Instant.now(), "{}"
    ));
    assertEquals(1, ok.get());
    assertEquals(0, fail.get());
  }

  @Test
  void publish_incrementsFailureCounter_andRethrows() {
    AtomicLong ok = new AtomicLong();
    AtomicLong fail = new AtomicLong();

    SchedulerMeter meter = new SchedulerMeter() {
      @Override public Counter counter(String name, String description) {
        if (name.contains("failed")) {
          return (v, labels) -> fail.addAndGet(v);
        }
        return (v, labels) -> ok.addAndGet(v);
      }
      @Override public Histogram histogram(String name, String description, String unit) { return (v, labels) -> {}; }
    };

    CommandBus delegate = command -> { throw new RuntimeException("boom"); };
    MeteredCommandBus bus = new MeteredCommandBus(delegate, meter);

    assertThrows(RuntimeException.class, () ->
        bus.publish(new CommandEnvelope(
            "t1", "cmd-1", "idem-1", "StartWorkflow", 123L, 1, Instant.now(), "{}"
        )));

    assertEquals(0, ok.get());
    assertEquals(1, fail.get());
  }
}
