package com.acme.scheduler.service.port;

import com.acme.scheduler.service.workflow.CommandEnvelope;

/**
 * Ingestion abstraction that supports:
 * - direct DB writes (low TPS)
 * - Kafka WAL writes (high TPS)
 *
 * Implementations live in adapters.
 */
public interface CommandIngestionGateway {

  enum ResultType { ACCEPTED, DUPLICATE, REJECTED }

  record Result(
      ResultType type,
      String message
  ) {
    public static Result accepted() { return new Result(ResultType.ACCEPTED, "accepted"); }
    public static Result duplicate() { return new Result(ResultType.DUPLICATE, "duplicate"); }
    public static Result rejected(String msg) { return new Result(ResultType.REJECTED, msg); }
  }

  Result ingest(CommandEnvelope command);
}
