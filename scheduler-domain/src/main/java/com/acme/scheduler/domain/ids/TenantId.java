package com.acme.scheduler.domain.ids;

import com.acme.scheduler.domain.DomainValidationException;

public record TenantId(String value) {
 public TenantId {
 if (value == null || value.isBlank()) throw new DomainValidationException("tenantId must be non-empty");
 }
}
