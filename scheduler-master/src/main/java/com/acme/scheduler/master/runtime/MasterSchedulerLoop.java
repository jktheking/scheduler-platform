package com.acme.scheduler.master.runtime;

import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.port.CommandQueue;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

public final class MasterSchedulerLoop {

  private final CommandQueue queue;
  private final MasterCommandProcessor processor;
  private final MasterMetrics metrics;

  public MasterSchedulerLoop(CommandQueue queue, MasterCommandProcessor processor, MasterMetrics metrics) {
    this.queue = queue;
    this.processor = processor;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${scheduler.master.command-poll-ms:200}")
  public void tick() {
    List<CommandRecord> claimed = queue.claimBatch(100);
    if (!claimed.isEmpty()) metrics.commandClaimed.add(claimed.size());
    for (CommandRecord cmd : claimed) {
      processor.process(cmd);
    }
  }
}
