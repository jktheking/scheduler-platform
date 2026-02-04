package com.acme.scheduler.domain.state;

import java.util.EnumSet;
import java.util.Set;

/**
 * Workflow instance lifecycle.
 * Intentionally DS-inspired but simplified for the domain layer.
 */
public enum WorkflowExecutionStatus {
 SUBMITTED,
 RUNNING,
 PAUSED,
 SUCCESS,
 FAILURE,
 KILL;

 public boolean isTerminal() {
 return this == SUCCESS || this == FAILURE || this == KILL;
 }

 public Set<WorkflowExecutionStatus> allowedNext() {
 return switch (this) {
 case SUBMITTED -> EnumSet.of(RUNNING, KILL);
 case RUNNING -> EnumSet.of(PAUSED, SUCCESS, FAILURE, KILL);
 case PAUSED -> EnumSet.of(RUNNING, KILL);
 case SUCCESS, FAILURE, KILL -> EnumSet.noneOf(WorkflowExecutionStatus.class);
 };
 }

 public boolean canTransitionTo(WorkflowExecutionStatus next) {
 return allowedNext().contains(next);
 }
}
