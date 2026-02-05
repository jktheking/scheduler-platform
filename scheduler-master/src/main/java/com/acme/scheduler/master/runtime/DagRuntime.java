package com.acme.scheduler.master.runtime;

import com.acme.scheduler.common.runtime.TaskStateEvent;

import java.time.Instant;

public interface DagRuntime {
  void onTriggerDue(long workflowInstanceId, long triggerId, Instant dueTime, String triggerPayloadJson);

  void onTaskState(TaskStateEvent evt);
}
