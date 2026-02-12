package com.acme.scheduler.common.alert;

/** High-level alert categories emitted by scheduler-master. */
public enum AlertType {
  WORKFLOW_FAILED,
  DLQ_CREATED,
  RETRY_SCHEDULED,
  SLA_BREACH,
  TASK_TIMEOUT
}
