package com.acme.scheduler.master.port;

import com.acme.scheduler.master.runtime.CommandRecord;

import java.util.List;

public interface CommandQueue {
  List<CommandRecord> claimBatch(int limit);
  void markDone(String commandId);
  void markFailed(String commandId, String error);
}
