package com.acme.scheduler.adapter.jdbc;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.acme.scheduler.service.port.CommandIngestionGateway;
import com.acme.scheduler.service.port.CommandRepository;
import com.acme.scheduler.service.workflow.CommandEnvelope;

/**
 * Low-TPS ingestion path: write command directly to DB.
 *
 * Note: In this mode, masters may poll DB (or a separate reconciler can publish to Kafka).
 */
public final class JdbcCommandIngestionGateway implements CommandIngestionGateway {

  private static final Logger log = LoggerFactory.getLogger(JdbcCommandIngestionGateway.class);

  private final CommandRepository repo;

  public JdbcCommandIngestionGateway(CommandRepository repo) {
    this.repo = Objects.requireNonNull(repo);
  }

  @Override
  public Result ingest(CommandEnvelope command) {
    log.info("checkpoint=ingest.gateway mode=JDBC tenantId={} commandId={} idempotencyKey={} commandType={} workflowCode={} workflowVersion={} createdAt={}",
        command.tenantId(), command.commandId(), command.idempotencyKey(), command.commandType(), command.workflowCode(), command.workflowVersion(), command.createdAt());
    var r = repo.insertIfAbsent(command);
    if (r == CommandRepository.InsertResult.DUPLICATE) {
      log.info("checkpoint=ingest.command_duplicate tenantId={} idempotencyKey={} workflowCode={} workflowVersion={} commandId={}",
          command.tenantId(), command.idempotencyKey(), command.workflowCode(), command.workflowVersion(), command.commandId());
      return Result.duplicate();
    }
    return Result.accepted();
  }
}
