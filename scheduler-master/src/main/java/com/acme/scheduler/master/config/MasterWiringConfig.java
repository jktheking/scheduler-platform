package com.acme.scheduler.master.config;

import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.meter.SchedulerMeter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MasterWiringConfig {
  @Bean
  public MasterMetrics masterMetrics(SchedulerMeter meter) {
    return new MasterMetrics(meter);
  }
}
