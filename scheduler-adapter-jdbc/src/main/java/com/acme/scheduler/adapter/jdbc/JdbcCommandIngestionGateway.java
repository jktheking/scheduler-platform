package com.acme.scheduler.adapter.jdbc;

import java.util.Objects;

import com.acme.scheduler.service.port.CommandIngestionGateway;
import com.acme.scheduler.service.port.CommandRepository;
import com.acme.scheduler.service.workflow.CommandEnvelope;

/**
 * Low-TPS ingestion path: write command directly to DB.
 *
 * Note: In this mode, masters may poll DB (or a separate reconciler can publish to Kafka).
 */
public final class JdbcCommandIngestionGateway implements CommandIngestionGateway {

  private final CommandRepository repo;

  public JdbcCommandIngestionGateway(CommandRepository repo) {
    this.repo = Objects.requireNonNull(repo);
  }

  @Override
  public Result ingest(CommandEnvelope command) {
    var r = repo.insertIfAbsent(command);
    if (r == CommandRepository.InsertResult.DUPLICATE) return Result.duplicate();
    return Result.accepted();
  }
}
