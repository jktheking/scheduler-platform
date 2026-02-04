package com.acme.scheduler.domain.ids;

import com.acme.scheduler.domain.DomainValidationException;

public record TaskInstanceId(long value) {
 public TaskInstanceId {
 if (value <= 0) throw new DomainValidationException("taskInstanceId must be positive");
 }
}
