package com.acme.scheduler.domain.alert;

import com.acme.scheduler.common.alert.AlertEvent;

/** Default no-op publisher (used when alerting is disabled/unconfigured). */
public final class NoopAlertPublisher implements AlertPublisher {
  @Override
  public void publish(AlertEvent event) {
    // no-op
  }
}
