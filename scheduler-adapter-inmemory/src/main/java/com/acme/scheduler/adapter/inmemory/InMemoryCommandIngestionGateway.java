package com.acme.scheduler.adapter.inmemory;

import java.util.Objects;

import com.acme.scheduler.service.port.CommandBus;
import com.acme.scheduler.service.port.CommandIngestionGateway;
import com.acme.scheduler.service.port.CommandRepository;
import com.acme.scheduler.service.workflow.CommandEnvelope;

/**
 * Dev/test ingestion: write to in-memory repo, then publish to in-memory bus.
 */
public final class InMemoryCommandIngestionGateway implements CommandIngestionGateway {

  private final CommandRepository repo;
  private final CommandBus bus;

  public InMemoryCommandIngestionGateway(CommandRepository repo, CommandBus bus) {
    this.repo = Objects.requireNonNull(repo);
    this.bus = Objects.requireNonNull(bus);
  }

  @Override
  public Result ingest(CommandEnvelope command) {
    var r = repo.insertIfAbsent(command);
    if (r == CommandRepository.InsertResult.DUPLICATE) return Result.duplicate();
    bus.publish(command);
    return Result.accepted();
  }
}
