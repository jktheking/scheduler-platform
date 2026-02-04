package com.acme.scheduler.domain.ids;

import com.acme.scheduler.domain.DomainValidationException;

public record WorkflowInstanceId(long value) {
 public WorkflowInstanceId {
 if (value <= 0) throw new DomainValidationException("workflowInstanceId must be positive");
 }
}
