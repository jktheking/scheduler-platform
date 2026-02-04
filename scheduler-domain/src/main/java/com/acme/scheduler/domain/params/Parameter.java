package com.acme.scheduler.domain.params;

import com.acme.scheduler.domain.DomainValidationException;

public record Parameter(String prop, String value) {
 public Parameter {
 if (prop == null || prop.isBlank()) throw new DomainValidationException("parameter prop must be non-empty");
 if (value == null) throw new DomainValidationException("parameter value must be non-null");
 }
}
