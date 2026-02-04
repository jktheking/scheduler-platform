package com.acme.scheduler.master.trigger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.acme.scheduler.master.adapter.jdbc.JdbcTriggerRepository;
import com.acme.scheduler.master.kafka.KafkaReadyPublisher;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.runtime.TaskReadyEvent;

/**
 * Hashed-wheel-timer-inspired trigger engine.
 *
 * Design notes (v1 pragmatic):
 * - Triggers are persisted in DB (t_trigger).
 * - A loader pre-enqueues triggers within a lookahead window into in-memory wheels and flips status DUE->ENQUEUED.
 * - Each tick drains the current slot and processes triggers that are now due (claim+publish+mark done).
 *
 * This avoids scanning the entire trigger table on every poll and handles bursts better than plain polling.
 */
public final class TimeWheelTriggerEngine implements TriggerEngine {

  private static final Logger log = LoggerFactory.getLogger(TimeWheelTriggerEngine.class);

  private final JdbcTriggerRepository repo;
  private final KafkaReadyPublisher publisher;
  private final MasterMetrics metrics;

  private final int shards;
  private final long tickMs;
  private final int slots;
  private final long lookaheadMs;
  private final int drainBatch;
  private final String claimedBy;

  private final ScheduledExecutorService tickScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r);
    t.setName("timewheel-ticker");
    t.setDaemon(true);
    return t;
  });
  private final ScheduledExecutorService loaderScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r);
    t.setName("timewheel-loader");
    t.setDaemon(true);
    return t;
  });
  private final ExecutorService workerPool = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("timewheel-proc-", 0).factory());

  private final List<WheelShard> wheel;
  private final AtomicBoolean started = new AtomicBoolean(false);

  public TimeWheelTriggerEngine(JdbcTriggerRepository repo,
                               KafkaReadyPublisher publisher,
                               MasterMetrics metrics,
                               int shards,
                               long tickMs,
                               int slots,
                               long lookaheadMs,
                               int drainBatch) {
    this.repo = Objects.requireNonNull(repo);
    this.publisher = Objects.requireNonNull(publisher);
    this.metrics = Objects.requireNonNull(metrics);
    this.shards = shards;
    this.tickMs = tickMs;
    this.slots = slots;
    this.lookaheadMs = lookaheadMs;
    this.drainBatch = drainBatch;
    this.claimedBy = "master-" + UUID.randomUUID();

    List<WheelShard> tmp = new ArrayList<>(shards);
    for (int i = 0; i < shards; i++) tmp.add(new WheelShard(slots));
    this.wheel = List.copyOf(tmp);
  }

  @Override
  public void start() {
    if (!started.compareAndSet(false, true)) return;
    // Loader runs slower; ticker runs at tickMs.
    loaderScheduler.scheduleWithFixedDelay(this::loadUpcoming, 0, Math.max(200, tickMs), TimeUnit.MILLISECONDS);
    tickScheduler.scheduleWithFixedDelay(this::tick, 0, tickMs, TimeUnit.MILLISECONDS);
    log.info("TimeWheelTriggerEngine started shards={} tickMs={} slots={} lookaheadMs={}", shards, tickMs, slots, lookaheadMs);
  }

  @Override
  public void stop() {
    loaderScheduler.shutdownNow();
    tickScheduler.shutdownNow();
    workerPool.shutdownNow();
    log.info("TimeWheelTriggerEngine stopped");
  }

  private void loadUpcoming() {
    try {
      Instant upper = Instant.now().plusMillis(lookaheadMs);
      // Conservative cap: load up to 10x drainBatch per cycle
      List<JdbcTriggerRepository.TriggerRow> upcoming = repo.loadUpcoming(upper, Math.max(1000, drainBatch * 10));
      for (JdbcTriggerRepository.TriggerRow tr : upcoming) {
        // Flip state to ENQUEUED to avoid repeated wheel inserts; benign if concurrent masters race.
        repo.markEnqueued(tr.triggerId());
        int shard = shardFor(tr.triggerId());
        wheel.get(shard).add(tr, tickMs);
      }
    } catch (Exception e) {
      metrics.triggerError.add(1);
      log.warn("TimeWheel loader error: {}", e.toString());
    }
  }

  private void tick() {
    try {
      long nowMs = System.currentTimeMillis();
      // Drain each shard's current slot quickly; process concurrently in virtual threads.
      for (int s = 0; s < shards; s++) {
        List<JdbcTriggerRepository.TriggerRow> due = wheel.get(s).drainSlot(nowMs, tickMs);
        if (due.isEmpty()) continue;
        // Process up to drainBatch; excess stays for subsequent ticks.
        int toProcess = Math.min(due.size(), drainBatch);
        List<JdbcTriggerRepository.TriggerRow> batch = due.subList(0, toProcess);
        metrics.triggerClaimed.add(batch.size());
        workerPool.execute(() -> processBatch(batch));
        // push back remainder
        if (due.size() > toProcess) {
          for (int i = toProcess; i < due.size(); i++) {
            wheel.get(s).reAdd(due.get(i), tickMs);
          }
        }
      }
    } catch (Exception e) {
      metrics.triggerError.add(1);
      log.warn("TimeWheel tick error: {}", e.toString());
    }
  }

  private void processBatch(List<JdbcTriggerRepository.TriggerRow> batch) {
    try {
      // Claim in DB to handle multi-master correctness; claimDue uses SKIP LOCKED and due_time <= now()
      // We re-claim per trigger by issuing claimDue in chunks to leverage DB locking and avoid double publish.
      // For v1, we just publish events for already-claimed triggers via claimDue batch.
      List<JdbcTriggerRepository.TriggerRow> claimed = repo.claimDue(batch.size(), claimedBy);
      for (JdbcTriggerRepository.TriggerRow tr : claimed) {
        publisher.publish(new TaskReadyEvent(tr.workflowInstanceId(), tr.triggerId(), tr.dueTime(), "{}"));
        repo.markDone(tr.triggerId());
        metrics.triggerProcessed.add(1);
      }
    } catch (Exception e) {
      metrics.triggerError.add(1);
      log.warn("TimeWheel processBatch error: {}", e.toString());
    }
  }

  private int shardFor(long triggerId) {
    long h = triggerId ^ (triggerId >>> 33) ^ (triggerId << 11);
    return (int) (Math.abs(h) % shards);
  }

  private static final class WheelShard {
    private final ConcurrentLinkedQueue<JdbcTriggerRepository.TriggerRow>[] ring;
    private final AtomicLong tick = new AtomicLong(0);

    @SuppressWarnings("unchecked")
    WheelShard(int slots) {
      this.ring = new ConcurrentLinkedQueue[slots];
      for (int i = 0; i < slots; i++) ring[i] = new ConcurrentLinkedQueue<>();
    }

    void add(JdbcTriggerRepository.TriggerRow tr, long tickMs) {
      long dueMs = tr.dueTime().toEpochMilli();
      long nowMs = System.currentTimeMillis();
      long delta = Math.max(0, dueMs - nowMs);
      long ticksAway = delta / tickMs;
      int slot = (int) ((tick.get() + ticksAway) % ring.length);
      ring[slot].add(tr);
    }

    void reAdd(JdbcTriggerRepository.TriggerRow tr, long tickMs) {
      add(tr, tickMs);
    }

    List<JdbcTriggerRepository.TriggerRow> drainSlot(long nowMs, long tickMs) {
      int slot = (int) (tick.getAndIncrement() % ring.length);
      List<JdbcTriggerRepository.TriggerRow> out = new ArrayList<>();
      ConcurrentLinkedQueue<JdbcTriggerRepository.TriggerRow> q = ring[slot];
      JdbcTriggerRepository.TriggerRow tr;
      while ((tr = q.poll()) != null) {
        // Only process if truly due; otherwise re-add.
        if (tr.dueTime().toEpochMilli() <= nowMs) out.add(tr);
        else reAdd(tr, tickMs);
      }
      return out;
    }
  }
}
