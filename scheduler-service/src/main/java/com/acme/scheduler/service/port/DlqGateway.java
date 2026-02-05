package com.acme.scheduler.service.port;

import java.time.Instant;
import java.util.List;

/** Clean-arch port: DLQ management. */
public interface DlqGateway {

  List<DlqItem> list(String tenantId, int offset, int limit);

  void replay(long dlqId);

  record DlqItem(long dlqId,
                 long workflowInstanceId,
                 long taskInstanceId,
                 String reason,
                 Instant createdAt) {}
}
