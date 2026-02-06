package com.acme.scheduler.adapter.kafka;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.acme.scheduler.service.port.CommandBus;
import com.acme.scheduler.service.port.CommandIngestionGateway;
import com.acme.scheduler.service.workflow.CommandEnvelope;

/**
 * High-TPS ingestion path: write to Kafka as WAL.
 *
 * A separate consumer group persists commands to DB for idempotency and recovery.
 */
public final class KafkaWalCommandIngestionGateway implements CommandIngestionGateway {

  private static final Logger log = LoggerFactory.getLogger(KafkaWalCommandIngestionGateway.class);

  private final CommandBus bus;

  public KafkaWalCommandIngestionGateway(CommandBus bus) {
    this.bus = Objects.requireNonNull(bus);
  }

  @Override
  public Result ingest(CommandEnvelope command) {
    log.info("checkpoint=ingest.gateway mode=KAFKA tenantId={} commandId={} idempotencyKey={} commandType={} workflowCode={} workflowVersion={} createdAt={}",
        command.tenantId(), command.commandId(), command.idempotencyKey(), command.commandType(), command.workflowCode(), command.workflowVersion(), command.createdAt());
    bus.publish(command);
    return Result.accepted();
  }
}
