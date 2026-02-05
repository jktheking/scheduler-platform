package com.acme.scheduler.meter;

import java.util.Map;

public interface SchedulerMeter {
  Counter counter(String name, String description);

  /** Long histogram for durations/latencies and other distributions. */
  Histogram histogram(String name, String description, String unit);

  interface Counter {
    void add(long value, Map<String, String> labels);

    default void add(long value) {
      add(value, Map.of());
    }

    default void add(long value, String k1, String v1) {
      add(value, Map.of(k1, v1));
    }
  }

  interface Histogram {
    void record(long value, Map<String, String> labels);

    default void record(long value) {
      record(value, Map.of());
    }

    default void record(long value, String k1, String v1) {
      record(value, Map.of(k1, v1));
    }
  }
}
