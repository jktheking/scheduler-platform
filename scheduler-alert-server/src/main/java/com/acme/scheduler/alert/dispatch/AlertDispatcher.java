package com.acme.scheduler.alert.dispatch;

import com.acme.scheduler.common.alert.AlertEvent;

import java.util.List;
import java.util.Objects;

/** Dispatches alert events to external systems (logging, webhook, etc.). */
public interface AlertDispatcher {

  void dispatch(AlertEvent event);

  static AlertDispatcher composite(List<AlertDispatcher> dispatchers) {
    Objects.requireNonNull(dispatchers);
    return event -> {
      for (AlertDispatcher d : dispatchers) {
        try {
          d.dispatch(event);
        } catch (Exception ignored) {
          // Individual dispatcher failures must not prevent other dispatchers.
        }
      }
    };
  }
}
