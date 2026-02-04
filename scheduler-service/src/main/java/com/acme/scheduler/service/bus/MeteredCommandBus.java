package com.acme.scheduler.service.bus;

import java.util.Map;

import com.acme.scheduler.meter.SchedulerMeter;
import com.acme.scheduler.service.port.CommandBus;
import com.acme.scheduler.service.workflow.CommandEnvelope;

public final class MeteredCommandBus implements CommandBus {
  private final CommandBus delegate;
  private final SchedulerMeter.Counter published;
  private final SchedulerMeter.Counter publishFailed;

  public MeteredCommandBus(CommandBus delegate, SchedulerMeter meter) {
    this.delegate = delegate;
    this.published = meter.counter("scheduler_command_publish_total", "Commands published to bus");
    this.publishFailed = meter.counter("scheduler_command_publish_failed_total", "Command publish failures");
  }

  @Override
  public void publish(CommandEnvelope command) {
    Map<String, String> labels = Map.of(
        "command_type", command.commandType(),
        "tenant_id", command.tenantId()
    );

    try {
      delegate.publish(command);
      published.add(1, labels);
    } catch (RuntimeException e) {
      publishFailed.add(1, labels);
      throw e;
    }
  }
}
