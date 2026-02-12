package com.acme.scheduler.master.runtime;

import com.acme.scheduler.common.runtime.TaskStateEvent;

import java.time.Instant;

public interface DagRuntime {
  void onTriggerDue(long workflowInstanceId, long triggerId, Instant dueTime, String triggerPayloadJson);

  /**
   * Workflow SLA due callback (master-owned). Should be side-effect free for successful/terminal workflows.
   */
  void onWorkflowSlaDue(long workflowInstanceId, long triggerId, Instant dueTime);

  void onTaskState(TaskStateEvent evt);
}
