package com.acme.scheduler.service.port;

import com.acme.scheduler.service.workflow.CommandEnvelope;

public interface CommandBus {
  void publish(CommandEnvelope command);
}
