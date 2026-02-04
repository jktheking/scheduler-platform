package com.acme.scheduler.domain.ids;

import com.acme.scheduler.domain.DomainValidationException;

public record UserId(long value) {
 public UserId {
 if (value <= 0) throw new DomainValidationException("userId must be positive");
 }
}
