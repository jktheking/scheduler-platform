package com.acme.scheduler.domain.alert;

import com.acme.scheduler.common.alert.AlertEvent;

/** Domain port for publishing alert events. */
public interface AlertPublisher {
  void publish(AlertEvent event);
}
