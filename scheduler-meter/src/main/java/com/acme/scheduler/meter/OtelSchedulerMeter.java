package com.acme.scheduler.meter;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Map;

public final class OtelSchedulerMeter implements SchedulerMeter {
  private final Meter meter;

  public OtelSchedulerMeter(Meter meter) {
    this.meter = meter;
  }

  @Override
  public Counter counter(String name, String description) {
    LongCounter c = meter.counterBuilder(name).setDescription(description).build();
    return (value, labels) -> c.add(value, toAttributes(labels));
  }

  private static Attributes toAttributes(Map<String, String> labels) {
    var b = Attributes.builder();
    if (labels != null) {
      labels.forEach(b::put);
    }
    return b.build();
  }
}
