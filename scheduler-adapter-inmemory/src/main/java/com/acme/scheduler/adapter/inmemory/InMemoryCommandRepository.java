package com.acme.scheduler.adapter.inmemory;

import java.util.concurrent.ConcurrentHashMap;

import com.acme.scheduler.service.port.CommandRepository;
import com.acme.scheduler.service.workflow.CommandEnvelope;

/**
 * Dev/test adapter: in-memory idempotent command store.
 * Thread-safe, but NOT durable. Replaced by JDBC/Cassandra implementations later.
 */
public final class InMemoryCommandRepository implements CommandRepository {

  private final ConcurrentHashMap<String, CommandEnvelope> byIdempotencyKey = new ConcurrentHashMap<>();

  @Override
  public InsertResult insertIfAbsent(CommandEnvelope command) {
    CommandEnvelope existing = byIdempotencyKey.putIfAbsent(command.idempotencyKey(), command);
    return existing == null ? InsertResult.INSERTED : InsertResult.DUPLICATE;
  }
}
