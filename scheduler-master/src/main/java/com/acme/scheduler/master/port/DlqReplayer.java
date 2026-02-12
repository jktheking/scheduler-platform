package com.acme.scheduler.master.port;

/**
 * Master-owned DLQ replay.
 *
 * Replaying a DLQ entry is a control-plane mutation: it creates a new task attempt row and
 * schedules an immediate trigger to re-enqueue the task.
 */
public interface DlqReplayer {

  /** Apply a replay request for the given DLQ id. Implementations must be idempotent. */
  void replay(long dlqId);
}
