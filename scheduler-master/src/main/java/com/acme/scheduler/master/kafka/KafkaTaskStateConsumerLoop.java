package com.acme.scheduler.master.kafka;

import com.acme.scheduler.common.runtime.TaskStateEvent;
import com.acme.scheduler.master.config.MasterKafkaProperties;
import com.acme.scheduler.master.ha.ShardLeaderElector;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.runtime.DagRuntime;
import com.acme.scheduler.master.sharding.ShardCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Master consumer loop for worker TaskState events.
 *
 * Correctness fences: - Non-sharded: only leader(0) subscribes/consumes. Others
 * do not poll. - Sharded: consume only partitions for shards currently led. If
 * we lead no shards, do not poll. - If a polled batch contains any record we
 * did NOT apply due to shard leadership mismatch, we do NOT commit offsets for
 * that batch.
 */
public final class KafkaTaskStateConsumerLoop implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(KafkaTaskStateConsumerLoop.class);

	private final MasterKafkaProperties props;
	private final KafkaConsumer<String, byte[]> consumer;
	private final DagRuntime dagRuntime;
	private final ShardLeaderElector shardElector;
	private final ShardCalculator shardCalculator;

	private final String topic;
	private final boolean shardingEnabled;
	private final ObjectMapper mapper;
	private final MasterMetrics metrics;

	private final AtomicBoolean running = new AtomicBoolean(false);

	private final ExecutorService loop = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r);
		t.setName("kafka-task-state-consumer");
		t.setDaemon(true);
		return t;
	});

	// non-sharded subscription state
	private volatile boolean subscribed = false;

	// sharded assignment cache
	private volatile Set<Integer> lastLeaderShards = Set.of();

	public KafkaTaskStateConsumerLoop(MasterKafkaProperties props, KafkaConsumer<String, byte[]> consumer,
			DagRuntime dagRuntime, ShardLeaderElector shardElector, ShardCalculator shardCalculator,
			boolean shardingEnabled, ObjectMapper mapper, MasterMetrics metrics) {
		this.props = Objects.requireNonNull(props);
		this.topic = Objects.requireNonNull(props.getKafka().getTaskStateTopic());
		this.consumer = Objects.requireNonNull(consumer);
		this.dagRuntime = Objects.requireNonNull(dagRuntime);
		this.shardElector = Objects.requireNonNull(shardElector);
		this.shardCalculator = Objects.requireNonNull(shardCalculator);
		this.shardingEnabled = shardingEnabled;
		this.mapper = Objects.requireNonNull(mapper);
		this.metrics = Objects.requireNonNull(metrics);
	}

	public void start() {
		if (!running.compareAndSet(false, true))
			return;
		loop.execute(this);
	}

	public void stop() {
		running.set(false);
		try {
			consumer.wakeup();
		} catch (Exception ignored) {
		}
		loop.shutdownNow();
	}

	private void refreshAssignmentIfNeeded(boolean force) {
		Set<Integer> leader = shardElector.leaderShards();
		if (!force && leader.equals(lastLeaderShards))
			return;
		lastLeaderShards = leader;

		try {
			var parts = consumer.partitionsFor(topic, Duration.ofSeconds(5));
			int partitionCount = parts == null ? 0 : parts.size();

			if (partitionCount <= 0) {
				consumer.subscribe(List.of(topic));
				subscribed = true;
				return;
			}

			List<TopicPartition> assign = new ArrayList<>();
			for (Integer shardId : leader) {
				if (shardId == null)
					continue;
				int p = Math.floorMod(shardId, partitionCount);
				assign.add(new TopicPartition(topic, p));
			}

			if (assign.isEmpty()) {
				consumer.assign(List.of());
				log.info("checkpoint=master.task_state_assignment leaderShards={} assignedPartitions=EMPTY", leader);
				return;
			}

			consumer.assign(assign);
			log.info("checkpoint=master.task_state_assignment leaderShards={} assignedPartitions={}", leader, assign);
		} catch (Exception e) {
			log.warn("checkpoint=master.task_state_assignment_failed error={}", e.toString());
			// run() remains safe because it won't poll when assignment is empty.
		}
	}

	@Override
	public void run() {
		try {
			while (running.get()) {
				try {
					// Non-sharded HA fence: only leader(0) subscribes and polls.
					if (!shardingEnabled) {
						boolean leader0 = shardElector.isLeader(0);

						if (leader0 && !subscribed) {
							consumer.subscribe(List.of(topic));
							subscribed = true;
							log.info("checkpoint=master.task_state_subscribed mode=non_sharded leader0=true");
						} else if (!leader0 && subscribed) {
							consumer.unsubscribe();
							subscribed = false;
							log.info("checkpoint=master.task_state_unsubscribed mode=non_sharded leader0=false");
						}

						if (!leader0) {
							Thread.sleep(250);
							continue; // do not poll
						}
					}

					// Sharded: assign only leader shards. If none, do not poll.
					if (shardingEnabled) {
						refreshAssignmentIfNeeded(false);

						if (consumer.assignment().isEmpty()) {
							Thread.sleep(250);
							continue; // do not poll
						}
					}

					ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(200));
					if (records.isEmpty())
						continue;

					boolean allApplied = true;

					for (var r : records) {
						try {
							TaskStateEvent evt = mapper.readValue(r.value(), TaskStateEvent.class);
							metrics.taskStateKafkaConsumed.add(1);

							if (shardingEnabled) {
								int shardId = shardCalculator.shardForWorkflowInstanceId(evt.workflowInstanceId());
								if (!shardElector.isLeader(shardId)) {
									allApplied = false;
									log.debug(
											"checkpoint=master.task_state_skip_not_shard_leader workflowInstanceId={} shardId={} partition={} offset={}",
											evt.workflowInstanceId(), shardId, r.partition(), r.offset());
									break;
								}
							}

							dagRuntime.onTaskState(evt);
						} catch (Exception e) {
							allApplied = false;
							log.error(
									"checkpoint=master.task_state_parse_or_handle_failed topic={} partition={} offset={} error={}",
									r.topic(), r.partition(), r.offset(), e.toString());
							break;
						}
					}

					if (allApplied) {
						consumer.commitSync();
					}
				} catch (WakeupException ignore) {
					// normal on stop
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					log.warn("KafkaTaskStateConsumerLoop error: {}", e.toString());
					try {
						Thread.sleep(250);
					} catch (InterruptedException ignored) {
						Thread.currentThread().interrupt();
					}
				}
			}
		} finally {
			try {
				consumer.close();
			} catch (Exception ignored) {
			}
		}
	}
}