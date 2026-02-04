package com.acme.scheduler.domain.ids;

import com.acme.scheduler.domain.DomainValidationException;

public record IdempotencyKey(String value) {
 public IdempotencyKey {
 if (value == null || value.isBlank()) throw new DomainValidationException("idempotencyKey must be non-empty");
 if (value.length() > 128) throw new DomainValidationException("idempotencyKey too long (max 128)");
 }
}
