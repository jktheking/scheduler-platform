package com.acme.scheduler.service.dlq;

import com.acme.scheduler.service.port.DlqGateway;

import java.util.Objects;

public final class ReplayDlqUseCase {
  private final DlqGateway gw;

  public ReplayDlqUseCase(DlqGateway gw) {
    this.gw = Objects.requireNonNull(gw);
  }

  public void handle(long dlqId) {
    gw.replay(dlqId);
  }
}
