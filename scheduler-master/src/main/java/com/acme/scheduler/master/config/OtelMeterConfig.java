package com.acme.scheduler.master.config;

import com.acme.scheduler.meter.OtelSchedulerMeter;
import com.acme.scheduler.meter.SchedulerMeter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtelMeterConfig {
  @Bean
  public Meter meter() {
    return GlobalOpenTelemetry.get().getMeter("scheduler-master");
  }

  @Bean
  public SchedulerMeter schedulerMeter(Meter meter) {
    return new OtelSchedulerMeter(meter);
  }
}
