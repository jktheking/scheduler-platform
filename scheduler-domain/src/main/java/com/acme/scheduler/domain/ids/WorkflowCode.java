package com.acme.scheduler.domain.ids;

import com.acme.scheduler.domain.DomainValidationException;

public record WorkflowCode(long value) {
 public WorkflowCode {
 if (value <= 0) throw new DomainValidationException("workflowCode must be positive");
 }
}
