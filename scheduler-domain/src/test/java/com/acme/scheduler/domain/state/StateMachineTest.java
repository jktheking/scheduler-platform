package com.acme.scheduler.domain.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateMachineTest {

 @Test
 void workflowTransitionRules() {
 assertTrue(WorkflowExecutionStatus.SUBMITTED.canTransitionTo(WorkflowExecutionStatus.RUNNING));
 assertFalse(WorkflowExecutionStatus.SUBMITTED.canTransitionTo(WorkflowExecutionStatus.SUCCESS));
 assertTrue(WorkflowExecutionStatus.RUNNING.canTransitionTo(WorkflowExecutionStatus.SUCCESS));
 assertTrue(WorkflowExecutionStatus.RUNNING.canTransitionTo(WorkflowExecutionStatus.FAILURE));
 assertTrue(WorkflowExecutionStatus.RUNNING.canTransitionTo(WorkflowExecutionStatus.PAUSED));
 assertFalse(WorkflowExecutionStatus.SUCCESS.canTransitionTo(WorkflowExecutionStatus.RUNNING));
 }

 @Test
 void taskTransitionRules() {
 assertTrue(TaskExecutionStatus.SUBMITTED_SUCCESS.canTransitionTo(TaskExecutionStatus.DISPATCH));
 assertTrue(TaskExecutionStatus.DISPATCH.canTransitionTo(TaskExecutionStatus.RUNNING_EXECUTION));
 assertTrue(TaskExecutionStatus.RUNNING_EXECUTION.canTransitionTo(TaskExecutionStatus.NEED_FAULT_TOLERANCE));
 assertTrue(TaskExecutionStatus.NEED_FAULT_TOLERANCE.canTransitionTo(TaskExecutionStatus.SUBMITTED_SUCCESS));
 assertFalse(TaskExecutionStatus.SUCCESS.canTransitionTo(TaskExecutionStatus.DISPATCH));
 }
}
