package com.acme.scheduler.alert.dispatch;

import com.acme.scheduler.common.alert.AlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Always-on logging dispatcher for alerts. */
public final class LoggingAlertDispatcher implements AlertDispatcher {

  private static final Logger log = LoggerFactory.getLogger(LoggingAlertDispatcher.class);

  @Override
  public void dispatch(AlertEvent event) {
    if (event == null) return;
    log.info("checkpoint=alert.dispatch_log eventId={} type={} severity={} tenantId={} workflowInstanceId={} taskInstanceId={} attempt={} triggerId={} traceParent={} message={} details={}",
        event.eventId(), event.type(), event.severity(), event.tenantId(), event.workflowInstanceId(),
        event.taskInstanceId(), event.attempt(), event.triggerId(), event.traceParent(), event.message(), event.details());
  }
}
