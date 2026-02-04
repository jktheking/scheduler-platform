package com.acme.scheduler.adapter.kafka;

import java.util.Objects;

import com.acme.scheduler.service.port.CommandBus;
import com.acme.scheduler.service.port.CommandIngestionGateway;
import com.acme.scheduler.service.workflow.CommandEnvelope;

/**
 * High-TPS ingestion path: write to Kafka as WAL.
 *
 * A separate consumer group persists commands to DB for idempotency and recovery.
 */
public final class KafkaWalCommandIngestionGateway implements CommandIngestionGateway {

  private final CommandBus bus;

  public KafkaWalCommandIngestionGateway(CommandBus bus) {
    this.bus = Objects.requireNonNull(bus);
  }

  @Override
  public Result ingest(CommandEnvelope command) {
    bus.publish(command);
    return Result.accepted();
  }
}
