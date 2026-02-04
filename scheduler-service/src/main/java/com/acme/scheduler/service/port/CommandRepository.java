package com.acme.scheduler.service.port;

import com.acme.scheduler.service.workflow.CommandEnvelope;

public interface CommandRepository {

  enum InsertResult { INSERTED, DUPLICATE }

  /**
   * Insert command if absent (idempotency).
   * Enforce uniqueness by (tenant_id, idempotency_key) or command_id.
   */
  InsertResult insertIfAbsent(CommandEnvelope command);
}
