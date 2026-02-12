package com.acme.scheduler.service.dlq;

import com.acme.scheduler.service.port.CommandIngestionGateway;
import com.acme.scheduler.service.workflow.CommandEnvelope;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ReplayDlqUseCase {
  private final CommandIngestionGateway gateway;

  public ReplayDlqUseCase(CommandIngestionGateway gateway) {
    this.gateway = Objects.requireNonNull(gateway);
  }

  public void handle(long dlqId) {
    // DLQ replay is a master-owned mutation. Publish a WAL command so only the master leader applies it.
    String commandId = UUID.randomUUID().toString();
    String idempotencyKey = "dlq.replay." + dlqId;
    String payloadJson = "{\"dlqId\":" + dlqId + "}";

    var cmd = new CommandEnvelope(
        "default",
        commandId,
        idempotencyKey,
        "REPLAY_DLQ",
        0L,
        0,
        Instant.now(),
        payloadJson
    );

    CommandIngestionGateway.Result r = gateway.ingest(cmd);
    if (r.type() == CommandIngestionGateway.ResultType.REJECTED) {
      throw new IllegalStateException("DLQ replay rejected: " + r.message());
    }
  }
}
