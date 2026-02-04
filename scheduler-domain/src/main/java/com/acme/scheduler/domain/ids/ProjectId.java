package com.acme.scheduler.domain.ids;

import com.acme.scheduler.domain.DomainValidationException;

public record ProjectId(long value) {
 public ProjectId {
 if (value <= 0) throw new DomainValidationException("projectId must be positive");
 }
}
