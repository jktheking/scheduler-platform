package com.acme.scheduler.service.bus;

import com.acme.scheduler.service.port.BackpressureIndicators;
import com.acme.scheduler.service.port.CommandBus;
import com.acme.scheduler.service.workflow.CommandEnvelope;

import java.util.Objects;

public final class BackpressureCommandBus implements CommandBus {

  private final CommandBus delegate;
  private final BackpressureIndicators indicators;

  public BackpressureCommandBus(CommandBus delegate, BackpressureIndicators indicators) {
    this.delegate = Objects.requireNonNull(delegate);
    this.indicators = Objects.requireNonNull(indicators);
  }

  @Override
  public void publish(CommandEnvelope command) {
    if (indicators.isOverloaded()) {
      throw new IllegalStateException("command bus backpressure: " + indicators.reason());
    }
    delegate.publish(command);
  }
}

