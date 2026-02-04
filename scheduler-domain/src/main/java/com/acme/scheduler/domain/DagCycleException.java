package com.acme.scheduler.domain;

public class DagCycleException extends DomainValidationException {
 public DagCycleException(String message) {
 super(message);
 }
}
