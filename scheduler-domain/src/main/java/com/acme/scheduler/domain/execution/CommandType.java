package com.acme.scheduler.domain.execution;

/** command types (control-plane triggers). */
public enum CommandType {
 START_PROCESS,
 SCHEDULER,
 COMPLEMENT_DATA,
 RECOVER_TOLERANCE,
 START_CURRENT_TASK_PROCESS,
 START_FAILURE_TASK_PROCESS,
 STOP,
 PAUSE,
 RESUME
}
