package com.acme.scheduler.api.config;

import com.acme.scheduler.meter.OtelSchedulerMeter;
import com.acme.scheduler.meter.SchedulerMeter;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MeterConfig {

  @Bean
  public SchedulerMeter schedulerMeter() {
    Meter meter = GlobalOpenTelemetry.get().getMeter("scheduler-api");
    return new OtelSchedulerMeter(meter);
  }
}
