package com.acme.scheduler.adapter.kafka;

import com.acme.scheduler.service.port.KafkaPressureSampler;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

import java.util.Map;
import java.util.Objects;

/**
 * Samples producer-side pressure signals from KafkaProducer metrics.
 *
 * This is intentionally lightweight. For consumer-lag based pressure, use AdminClient in a separate component.
 */
public final class KafkaPressureSamplerImpl implements KafkaPressureSampler {

  private final KafkaCommandBus bus;

  public KafkaPressureSamplerImpl(KafkaCommandBus bus) {
    this.bus = Objects.requireNonNull(bus);
  }

  @Override
  public PressureSample sample() {
    try {
      Map<MetricName, ? extends Metric> m = bus.metrics();

      Double available = metricValue(m, "buffer-available-bytes");
      Double total = metricValue(m, "buffer-total-bytes");

      if (available != null && total != null && total > 0) {
        double usedRatio = 1.0 - (available / total);
        double pressure = clamp01(usedRatio);
        String reason = "producer_buffer_used_ratio=" + round3(usedRatio);
        return new PressureSample(pressure, reason);
      }

      // Fallback: if metrics not found, assume healthy.
      return PressureSample.healthy();
    } catch (Exception e) {
      return new PressureSample(0.5, "metrics_error:" + e.getClass().getSimpleName());
    }
  }

  private static Double metricValue(Map<MetricName, ? extends Metric> m, String metricName) {
    for (Map.Entry<MetricName, ? extends Metric> e : m.entrySet()) {
      if (metricName.equals(e.getKey().name())) {
        Object v = e.getValue().metricValue();
        if (v instanceof Number n) return n.doubleValue();
      }
    }
    return null;
  }

  private static double clamp01(double v) {
    if (v < 0) return 0;
    if (v > 1) return 1;
    return v;
  }

  private static String round3(double v) {
    return String.format(java.util.Locale.ROOT, "%.3f", v);
  }
}
