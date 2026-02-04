package com.acme.scheduler.master.runtime;

import com.acme.scheduler.domain.execution.CommandType;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.port.CommandQueue;
import com.acme.scheduler.master.port.WorkflowScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MasterCommandProcessor {

  private static final Logger log = LoggerFactory.getLogger(MasterCommandProcessor.class);

  private final CommandQueue queue;
  private final WorkflowScheduler scheduler;
  private final MasterMetrics metrics;

  public MasterCommandProcessor(CommandQueue queue, WorkflowScheduler scheduler, MasterMetrics metrics) {
    this.queue = queue;
    this.scheduler = scheduler;
    this.metrics = metrics;
  }

  public void process(CommandRecord cmd) {
    try {
      if (cmd.type() == CommandType.START_PROCESS) {
        long workflowInstanceId = scheduler.createAndSchedule(cmd.tenantId(), cmd.workflowCode(), cmd.workflowVersion(), cmd.payloadJson());
        metrics.workflowScheduled.add(1);
        log.info("Scheduled workflowInstanceId={} for tenantId={} workflowCode={}", workflowInstanceId, cmd.tenantId(), cmd.workflowCode());
      } else {
        log.warn("Unsupported command type: {}", cmd.type());
      }
      queue.markDone(cmd.commandId());
      metrics.commandProcessed.add(1);
    } catch (Exception e) {
      queue.markFailed(cmd.commandId(), e.getMessage());
      log.error("Command {} failed", cmd.commandId(), e);
    }
  }
}
