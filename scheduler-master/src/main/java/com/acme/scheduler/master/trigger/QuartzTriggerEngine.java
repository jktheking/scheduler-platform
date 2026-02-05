package com.acme.scheduler.master.trigger;

import com.acme.scheduler.master.adapter.jdbc.JdbcTriggerRepository;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.runtime.DagRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class QuartzTriggerEngine implements TriggerEngine {

  private static final Logger log = LoggerFactory.getLogger(QuartzTriggerEngine.class);

  private final JdbcTriggerRepository triggers;
  private final DagRuntime dagRuntime;
  private final MasterMetrics metrics;
  private final long pollMs;
  private final int batch;
  private final String claimedBy;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r);
    t.setName("quartz-trigger-poller");
    t.setDaemon(true);
    return t;
  });

  public QuartzTriggerEngine(JdbcTriggerRepository triggers,
                            DagRuntime dagRuntime,
                            MasterMetrics metrics,
                            long pollMs,
                            int batch) {
    this.triggers = Objects.requireNonNull(triggers);
    this.dagRuntime = Objects.requireNonNull(dagRuntime);
    this.metrics = Objects.requireNonNull(metrics);
    this.pollMs = pollMs;
    this.batch = batch;
    this.claimedBy = "master-" + UUID.randomUUID();
  }

  @Override
  public void start() {
    scheduler.scheduleWithFixedDelay(this::tick, 0, pollMs, TimeUnit.MILLISECONDS);
    log.info("QuartzTriggerEngine started pollMs={} batch={}", pollMs, batch);
  }

  @Override
  public void stop() {
    scheduler.shutdownNow();
    log.info("QuartzTriggerEngine stopped");
  }

  private void tick() {
    try {
      List<JdbcTriggerRepository.TriggerRow> claimed = triggers.claimDue(batch, claimedBy);
      if (!claimed.isEmpty()) metrics.triggerClaimed.add(claimed.size());
      for (JdbcTriggerRepository.TriggerRow tr : claimed) {
        try {
          dagRuntime.onTriggerDue(tr.workflowInstanceId(), tr.triggerId(), tr.dueTime(), "{}");
          triggers.markDone(tr.triggerId());
          metrics.triggerProcessed.add(1);
        } catch (Exception e) {
          triggers.markFailed(tr.triggerId(), e.toString());
          metrics.triggerError.add(1);
        }
      }
    } catch (Exception e) {
      metrics.triggerError.add(1);
      log.warn("QuartzTriggerEngine tick error: {}", e.toString());
    }
  }
}
