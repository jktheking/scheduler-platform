package com.acme.scheduler.master.runtime;

import com.acme.scheduler.domain.execution.CommandType;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.port.CommandQueue;
import com.acme.scheduler.master.port.DlqReplayer;
import com.acme.scheduler.master.port.WorkflowScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MasterCommandProcessor {

  private static final Logger log = LoggerFactory.getLogger(MasterCommandProcessor.class);

  private final CommandQueue queue;
  private final WorkflowScheduler scheduler;
  private final DlqReplayer dlqReplayer;
  private final MasterMetrics metrics;

  public MasterCommandProcessor(CommandQueue queue, WorkflowScheduler scheduler, DlqReplayer dlqReplayer, MasterMetrics metrics) {
    this.queue = queue;
    this.scheduler = scheduler;
    this.dlqReplayer = dlqReplayer;
    this.metrics = metrics;
  }

  public void process(CommandRecord cmd) {
    try {
      if (cmd.type() == CommandType.START_PROCESS) {
        long workflowInstanceId = scheduler.createAndSchedule(cmd.tenantId(), cmd.workflowCode(), cmd.workflowVersion(), cmd.payloadJson());
        metrics.workflowScheduled.add(1);
        log.info("Scheduled workflowInstanceId={} for tenantId={} workflowCode={}", workflowInstanceId, cmd.tenantId(), cmd.workflowCode());
      } else if (cmd.type() == CommandType.REPLAY_DLQ) {
        long dlqId = parseDlqId(cmd.payloadJson());
        dlqReplayer.replay(dlqId);
        metrics.dlqReplay.add(1);
        log.info("Replayed DLQ dlqId={} tenantId={}", dlqId, cmd.tenantId());
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

  private static long parseDlqId(String payloadJson) {
    // Payload format: {"dlqId":123}
    if (payloadJson == null) throw new IllegalArgumentException("payloadJson is null");
    int idx = payloadJson.indexOf("\"dlqId\"");
    if (idx < 0) throw new IllegalArgumentException("Missing dlqId in payload: " + payloadJson);
    int colon = payloadJson.indexOf(':', idx);
    if (colon < 0) throw new IllegalArgumentException("Invalid payload: " + payloadJson);
    int i = colon + 1;
    while (i < payloadJson.length() && Character.isWhitespace(payloadJson.charAt(i))) i++;
    int j = i;
    while (j < payloadJson.length() && Character.isDigit(payloadJson.charAt(j))) j++;
    if (j == i) throw new IllegalArgumentException("Invalid dlqId in payload: " + payloadJson);
    return Long.parseLong(payloadJson.substring(i, j));
  }
}
