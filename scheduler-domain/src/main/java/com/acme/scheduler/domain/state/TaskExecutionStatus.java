package com.acme.scheduler.domain.state;

import java.util.EnumSet;
import java.util.Set;

/**
 * Task instance lifecycle.
 * DS-inspired states with "submitted->dispatch->running->terminal" plus fault tolerance.
 */
public enum TaskExecutionStatus {
 SUBMITTED_SUCCESS,
 DISPATCH,
 RUNNING_EXECUTION,
 SUCCESS,
 FAILURE,
 KILL,
 NEED_FAULT_TOLERANCE;

 public boolean isTerminal() {
 return this == SUCCESS || this == FAILURE || this == KILL;
 }

 public Set<TaskExecutionStatus> allowedNext() {
 return switch (this) {
 case SUBMITTED_SUCCESS -> EnumSet.of(DISPATCH, KILL);
 case DISPATCH -> EnumSet.of(RUNNING_EXECUTION, KILL, NEED_FAULT_TOLERANCE);
 case RUNNING_EXECUTION -> EnumSet.of(SUCCESS, FAILURE, KILL, NEED_FAULT_TOLERANCE);
 case NEED_FAULT_TOLERANCE -> EnumSet.of(SUBMITTED_SUCCESS, KILL);
 case SUCCESS, FAILURE, KILL -> EnumSet.noneOf(TaskExecutionStatus.class);
 };
 }

 public boolean canTransitionTo(TaskExecutionStatus next) {
 return allowedNext().contains(next);
 }
}
