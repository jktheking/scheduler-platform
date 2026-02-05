package com.acme.scheduler.service.dlq;

import com.acme.scheduler.service.port.DlqGateway;

import java.util.List;
import java.util.Objects;

public final class ListDlqUseCase {
  private final DlqGateway gw;

  public ListDlqUseCase(DlqGateway gw) {
    this.gw = Objects.requireNonNull(gw);
  }

  public List<DlqGateway.DlqItem> handle(String tenantId, int offset, int limit) {
    return gw.list(tenantId, offset, limit);
  }
}
