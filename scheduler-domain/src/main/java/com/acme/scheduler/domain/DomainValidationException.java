package com.acme.scheduler.domain;

public class DomainValidationException extends RuntimeException {
 public DomainValidationException(String message) {
 super(message);
 }
}
