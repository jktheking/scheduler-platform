package com.acme.scheduler.master.kafka;

import com.acme.scheduler.master.adapter.jdbc.JdbcCommandDedupeRepository;
import com.acme.scheduler.master.config.MasterKafkaProperties;
import com.acme.scheduler.master.ha.ShardLeaderElector;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.port.DlqReplayer;
import com.acme.scheduler.master.port.WorkflowScheduler;
import com.acme.scheduler.master.sharding.ShardCalculator;
import com.acme.scheduler.service.workflow.CommandEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Consumer loop for {@code scheduler.commands.v1}.
 *
 * <p>
 * Correctness fences:
 * <ul>
 * <li>Non-sharded: only the "global leader" (shard 0 leader)
 * subscribes/consumes. Others do not poll.</li>
 * <li>Sharded: consume only partitions for shards currently led. If we lead no
 * shards, do not poll.</li>
 * <li>If a polled batch contains any record we did NOT apply due to shard
 * leadership mismatch, we do NOT commit offsets for that batch.</li>
 * </ul>
 */
public final class KafkaMasterCommandConsumerLoop implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(KafkaMasterCommandConsumerLoop.class);

	private final MasterKafkaProperties props;
	private final KafkaConsumer<String, byte[]> consumer;
	private final JdbcCommandDedupeRepository dedupe;
	private final WorkflowScheduler workflowScheduler;
	private final DlqReplayer dlqReplayer;
	private final ShardLeaderElector shardElector;
	private final ShardCalculator shardCalculator;
	private final boolean shardingEnabled;
	private final ObjectMapper mapper;
	private final MasterMetrics metrics;

	private final ExecutorService loop = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r);
		t.setName("kafka-master-consumer");
		t.setDaemon(true);
		return t;
	});

	private volatile boolean started = false;
	private volatile boolean running = false;

	// non-sharded subscription state
	private volatile boolean subscribed = false;

	// sharded assignment cache
	private volatile Set<Integer> lastLeaderShards = Set.of();

	public KafkaMasterCommandConsumerLoop(MasterKafkaProperties props, KafkaConsumer<String, byte[]> consumer,
			JdbcCommandDedupeRepository dedupe, WorkflowScheduler workflowScheduler, DlqReplayer dlqReplayer,
			ShardLeaderElector shardElector, ShardCalculator shardCalculator, boolean shardingEnabled,
			ObjectMapper mapper, MasterMetrics metrics) {
		this.props = Objects.requireNonNull(props);
		this.consumer = Objects.requireNonNull(consumer);
		this.dedupe = Objects.requireNonNull(dedupe);
		this.workflowScheduler = Objects.requireNonNull(workflowScheduler);
		this.dlqReplayer = Objects.requireNonNull(dlqReplayer);
		this.shardElector = Objects.requireNonNull(shardElector);
		this.shardCalculator = Objects.requireNonNull(shardCalculator);
		this.shardingEnabled = shardingEnabled;
		this.mapper = Objects.requireNonNull(mapper);
		this.metrics = Objects.requireNonNull(metrics);
	}

	@Override
	public void start() {
		if (started)
			return;
		started = true;
		running = true;

		// Do not subscribe/assign here; runLoop handles leadership gating (non-sharded)
		// and assignment refresh (sharded).
		loop.execute(this::runLoop);

		log.info("KafkaMasterCommandConsumerLoop started topic={} groupId={} shardingEnabled={}",
				props.getKafka().getCommandsTopic(), props.getKafka().getMasterGroupId(), shardingEnabled);
	}

	@Override
	public void stop() {
		started = false;
		running = false;
		try {
			consumer.wakeup();
		} catch (Exception ignored) {
		}
		loop.shutdownNow();
		try {
			consumer.close();
		} catch (Exception ignored) {
		}
		log.info("KafkaMasterCommandConsumerLoop stopped");
	}

	@Override
	public boolean isRunning() {
		return started && running;
	}

	@Override
	public int getPhase() {
		return 0;
	}

	private void runLoop() {
		final String topic = props.getKafka().getCommandsTopic();

		try {
			while (running) {
				try {
					// -----------------------
					// Non-sharded HA fence:
					// only leader(0) subscribes and polls.
					// -----------------------
					if (!shardingEnabled) {
						boolean leader0 = shardElector.isLeader(0);

						if (leader0 && !subscribed) {
							consumer.subscribe(List.of(topic));
							subscribed = true;
							log.info("checkpoint=master.commands_subscribed mode=non_sharded leader0=true topic={}",
									topic);
						} else if (!leader0 && subscribed) {
							consumer.unsubscribe();
							subscribed = false;
							log.info("checkpoint=master.commands_unsubscribed mode=non_sharded leader0=false topic={}",
									topic);
						}

						if (!leader0) {
							Thread.sleep(250);
							continue; // do not poll
						}
					}

					// -----------------------
					// Sharded assignment:
					// assign only leader shards. If none, do not poll.
					// -----------------------
					if (shardingEnabled) {
						refreshAssignmentIfNeeded(false);

						// If we currently lead no shards, we have no partitions assigned.
						// Polling would throw IllegalStateException.
						if (consumer.assignment().isEmpty()) {
							Thread.sleep(250);
							continue;
						}
					}

					ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(200));
					if (records.isEmpty())
						continue;

					boolean allApplied = true;

					for (ConsumerRecord<String, byte[]> rec : records) {
						CommandEnvelope cmd = mapper.readValue(rec.value(), CommandEnvelope.class);

						if (shardingEnabled) {
							// Shard by workflowCode (pre-instance) to keep command application
							// deterministic.
							int shardId = shardCalculator.shardForWorkflowCode(cmd.workflowCode());
							if (!shardElector.isLeader(shardId)) {
								// Hard fence: do NOT apply and do NOT commit offsets for this batch.
								allApplied = false;
								log.debug(
										"checkpoint=command_skip_not_shard_leader workflowCode={} shardId={} partition={} offset={}",
										cmd.workflowCode(), shardId, rec.partition(), rec.offset());
								break;
							}
						}

						metrics.commandKafkaConsumed.add(1);
						log.info(
								"checkpoint=master.command_consumed tenantId={} commandId={} workflowCode={} workflowVersion={} topic={} partition={} offset={}",
								cmd.tenantId(), cmd.commandId(), cmd.workflowCode(), cmd.workflowVersion(), rec.topic(),
								rec.partition(), rec.offset());

						boolean first = dedupe.tryMarkProcessing(cmd.commandId());
						if (!first) {
							log.info(
									"checkpoint=master.command_dedupe_skip commandId={} topic={} partition={} offset={}",
									cmd.commandId(), rec.topic(), rec.partition(), rec.offset());
							continue;
						}

						log.info("checkpoint=master.command_dedupe_accept commandId={}", cmd.commandId());

						try {
							if ("REPLAY_DLQ".equals(cmd.commandType())) {
								long dlqId = parseDlqId(cmd.payloadJson());
								dlqReplayer.replay(dlqId);
								metrics.dlqReplay.add(1);
								log.info("checkpoint=master.dlq_replay_applied tenantId={} commandId={} dlqId={}",
										cmd.tenantId(), cmd.commandId(), dlqId);
								dedupe.markOutcome(cmd.commandId(), "DONE", "dlqId=" + dlqId);

							} else if ("START_PROCESS".equals(cmd.commandType())) {
								long workflowInstanceId = workflowScheduler.createAndSchedule(cmd.tenantId(),
										cmd.workflowCode(), cmd.workflowVersion(), cmd.payloadJson());
								metrics.workflowScheduled.add(1);
								log.info(
										"checkpoint=master.workflow_scheduled tenantId={} commandId={} workflowInstanceId={} workflowCode={} workflowVersion={}",
										cmd.tenantId(), cmd.commandId(), workflowInstanceId, cmd.workflowCode(),
										cmd.workflowVersion());
								dedupe.markOutcome(cmd.commandId(), "DONE", "workflowInstanceId=" + workflowInstanceId);

							} else {
								log.warn(
										"checkpoint=master.command_unsupported tenantId={} commandId={} commandType={}",
										cmd.tenantId(), cmd.commandId(), cmd.commandType());
								dedupe.markOutcome(cmd.commandId(), "FAILED",
										"unsupported commandType=" + cmd.commandType());
							}
						} catch (Exception ex) {
							metrics.commandKafkaError.add(1);
							try {
								dedupe.markOutcome(cmd.commandId(), "FAILED", ex.getMessage());
							} catch (Exception ignored) {
							}
							log.error(
									"checkpoint=master.command_failed commandId={} tenantId={} workflowCode={} workflowVersion={} error={}",
									cmd.commandId(), cmd.tenantId(), cmd.workflowCode(), cmd.workflowVersion(),
									ex.toString());
							// Do not commit this batch; let it be retried (dedupe ensures idempotence).
							allApplied = false;
							break;
						}
					}

					if (allApplied) {
						consumer.commitSync();
					}
				} catch (WakeupException we) {
					// normal on stop
				} catch (InterruptedException ie) {
					// stop requested
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					metrics.commandKafkaError.add(1);
					log.warn("KafkaMasterCommandConsumerLoop error: {}", e.toString());
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

	private void refreshAssignmentIfNeeded(boolean force) {
		String topic = props.getKafka().getCommandsTopic();
		Set<Integer> leader = shardElector.leaderShards();
		if (!force && leader.equals(lastLeaderShards))
			return;
		lastLeaderShards = leader;

		try {
			int partitionCount = consumer.partitionsFor(topic, Duration.ofSeconds(5)).size();
			if (partitionCount <= 0) {
				// If topic metadata isn't available, fall back to subscribe to avoid invalid
				// state.
				consumer.subscribe(List.of(topic));
				subscribed = true;
				return;
			}

			// Best-effort: if partitions == shards, map shardId -> partitionId (direct).
			List<TopicPartition> assign = new ArrayList<>();
			for (Integer shardId : leader) {
				if (shardId != null && shardId >= 0 && shardId < partitionCount) {
					assign.add(new TopicPartition(topic, shardId));
				}
			}

			if (assign.isEmpty()) {
				// Clear assignment when not leading any shards.
				consumer.assign(List.of());
				log.info("checkpoint=master.commands_assignment leaderShards={} assignedPartitions=EMPTY", leader);
				return;
			}

			consumer.assign(assign);
			log.info("checkpoint=master.commands_assignment leaderShards={} assignedPartitions={}", leader, assign);
		} catch (Exception e) {
			log.warn("checkpoint=master.commands_assignment_failed error={}", e.toString());
			// Safe fallback: subscribe so consumer is in a valid state (and poll won't
			// crash),
			// but runLoop still fences on shard leadership when shardingEnabled=true.
			consumer.subscribe(List.of(topic));
			subscribed = true;
		}
	}

	private static long parseDlqId(String payloadJson) {
		if (payloadJson == null)
			throw new IllegalArgumentException("payloadJson is null");
		int idx = payloadJson.indexOf("\"dlqId\"");
		if (idx < 0)
			throw new IllegalArgumentException("Missing dlqId in payload: " + payloadJson);
		int colon = payloadJson.indexOf(':', idx);
		if (colon < 0)
			throw new IllegalArgumentException("Invalid payload: " + payloadJson);
		int i = colon + 1;
		while (i < payloadJson.length() && Character.isWhitespace(payloadJson.charAt(i)))
			i++;
		int j = i;
		while (j < payloadJson.length() && Character.isDigit(payloadJson.charAt(j)))
			j++;
		if (j == i)
			throw new IllegalArgumentException("Invalid dlqId in payload: " + payloadJson);
		return Long.parseLong(payloadJson.substring(i, j));
	}
}
