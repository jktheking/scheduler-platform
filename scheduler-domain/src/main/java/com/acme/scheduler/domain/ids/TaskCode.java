package com.acme.scheduler.domain.ids;

import com.acme.scheduler.domain.DomainValidationException;

public record TaskCode(long value) {
 public TaskCode {
 if (value <= 0) throw new DomainValidationException("taskCode must be positive");
 }
}
