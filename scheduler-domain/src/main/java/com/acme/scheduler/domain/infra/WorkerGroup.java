package com.acme.scheduler.domain.infra;

import com.acme.scheduler.domain.DomainValidationException;

public record WorkerGroup(String value) {
 public WorkerGroup {
 if (value == null || value.isBlank()) throw new DomainValidationException("workerGroup must be non-empty");
 }
}
