package com.acme.scheduler.domain.infra;

import com.acme.scheduler.domain.DomainValidationException;

public record EnvironmentCode(long value) {
 public EnvironmentCode {
 if (value < 0) throw new DomainValidationException("environmentCode must be >= 0");
 }
}
