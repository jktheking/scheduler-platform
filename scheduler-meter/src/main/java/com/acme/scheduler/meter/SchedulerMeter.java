package com.acme.scheduler.meter;

import java.util.Map;

public interface SchedulerMeter {
  Counter counter(String name, String description);

  interface Counter {
    void add(long value, Map<String, String> labels);

    /** Convenience overload with no labels. */
    default void add(long value) {
      add(value, Map.of());
    }

    /** Convenience overload for a single label. */
    default void add(long value, String k1, String v1) {
      add(value, Map.of(k1, v1));
    }
  }
}
