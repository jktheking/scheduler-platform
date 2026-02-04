package com.acme.scheduler.adapter.inmemory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.acme.scheduler.service.port.CommandBus;
import com.acme.scheduler.service.workflow.CommandEnvelope;

/**
 * In-memory bus for dev/test.
 */
public final class InMemoryCommandBus implements CommandBus {

  private final BlockingQueue<CommandEnvelope> queue = new LinkedBlockingQueue<>();

  @Override
  public void publish(CommandEnvelope command) {
    queue.offer(command);
  }

  public BlockingQueue<CommandEnvelope> queue() {
    return queue;
  }
}
