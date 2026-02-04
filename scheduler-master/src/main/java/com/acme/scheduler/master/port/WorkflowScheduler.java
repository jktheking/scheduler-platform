package com.acme.scheduler.master.port;

public interface WorkflowScheduler {
  /**
   * Create workflow instance and schedule its execution at the requested time.
   */
  long createAndSchedule(String tenantId, long workflowCode, int workflowVersion, String commandPayloadJson);
}
