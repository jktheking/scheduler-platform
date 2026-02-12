package com.acme.scheduler.master.runtime;

import com.acme.scheduler.common.runtime.TaskDispatchEnvelope;
import com.acme.scheduler.common.runtime.TaskStateEvent;
import com.acme.scheduler.master.adapter.jdbc.JdbcDagTaskInstanceRepository;
import com.acme.scheduler.master.adapter.jdbc.JdbcTriggerRepository;
import com.acme.scheduler.master.observability.MasterMetrics;
import com.acme.scheduler.master.kafka.KafkaReadyPublisher;
import com.acme.scheduler.common.alert.AlertEvent;
import com.acme.scheduler.common.alert.AlertSeverity;
import com.acme.scheduler.common.alert.AlertType;
import com.acme.scheduler.domain.alert.AlertPublisher;
import com.acme.scheduler.master.ha.ShardLeaderElector;
import com.acme.scheduler.master.sharding.ShardCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class DefaultDagRuntime implements DagRuntime {

	private static final Logger log = LoggerFactory.getLogger(DefaultDagRuntime.class);

	private final WorkflowMaterializer materializer;
	private final JdbcDagTaskInstanceRepository tasks;
	private final JdbcTemplateWorkflowInstanceStatus wi;
	private final JdbcTriggerRepository triggers;
	private final KafkaReadyPublisher readyPublisher;
	private final DagProgressionEngine progression;
	private final RetryPlanner retryPlanner;
	private final AlertPublisher alertPublisher;
	private final ShardLeaderElector shardElector;
	private final boolean shardingEnabled;
	private final ShardCalculator shardCalculator;
	private final ObjectMapper mapper;
	private final MasterMetrics metrics;

	public DefaultDagRuntime(WorkflowMaterializer materializer, JdbcDagTaskInstanceRepository tasks,
			JdbcTemplateWorkflowInstanceStatus wi, JdbcTriggerRepository triggers, KafkaReadyPublisher readyPublisher,
			DagProgressionEngine progression, RetryPlanner retryPlanner, AlertPublisher alertPublisher,
			ShardLeaderElector shardElector, ShardCalculator shardCalculator, ObjectMapper mapper,
			MasterMetrics metrics, boolean shardingEnabled) {
		this.materializer = Objects.requireNonNull(materializer);
		this.tasks = Objects.requireNonNull(tasks);
		this.wi = Objects.requireNonNull(wi);
		this.triggers = Objects.requireNonNull(triggers);
		this.readyPublisher = Objects.requireNonNull(readyPublisher);
		this.progression = Objects.requireNonNull(progression);
		this.retryPlanner = Objects.requireNonNull(retryPlanner);
		this.alertPublisher = Objects.requireNonNull(alertPublisher);
		this.shardElector = Objects.requireNonNull(shardElector);
		this.shardCalculator = Objects.requireNonNull(shardCalculator);
		this.mapper = Objects.requireNonNull(mapper);
		this.metrics = Objects.requireNonNull(metrics);
		this.shardingEnabled = shardingEnabled;
	}

	@Override
	public void onTriggerDue(long workflowInstanceId, long triggerId, Instant dueTime, String triggerPayloadJson) {
		int shardId = shardCalculator.shardForWorkflowInstanceId(workflowInstanceId);
		boolean leader = shardingEnabled ? shardElector.isLeader(shardId) : shardElector.isLeader(0);
		if (!leader) {
			log.debug("checkpoint=master.trigger_due_ignored_not_leader workflowInstanceId={} triggerId={} dueTime={}",
					workflowInstanceId, triggerId, dueTime);
			return;
		}
		if (wi.isTerminal(workflowInstanceId)) {
			log.info("checkpoint=master.trigger_due_ignored_terminal workflowInstanceId={} triggerId={} dueTime={}",
					workflowInstanceId, triggerId, dueTime);
			return;
		}

		var k = wi.loadInstanceKey(workflowInstanceId);
		boolean startedNow = wi.markWorkflowRunningIfStart(workflowInstanceId);

		var m = materializer.materializeIfAbsent(k.tenantId(), k.workflowCode(), k.workflowVersion(),
				workflowInstanceId);
		metrics.dagMaterializeCount.add(1);

		// Master-owned workflow SLA: schedule once when the workflow transitions into
		// RUNNING.
		if (startedNow) {
			long slaSeconds = 0L;
			try {
				var root = mapper.readTree(m.def().definitionJson());
				slaSeconds = root.path("slaSeconds").asLong(0L);
			} catch (Exception ignored) {
			}
			if (slaSeconds > 0L) {
				Instant slaDue = Instant.now().plusSeconds(slaSeconds);
				triggers.scheduleWorkflowSlaTrigger(workflowInstanceId, slaDue);
				log.info(
						"checkpoint=master.workflow_sla_scheduled workflowInstanceId={} slaSeconds={} slaDue={} triggerType={}",
						workflowInstanceId, slaSeconds, slaDue, JdbcTriggerRepository.TriggerTypes.WORKFLOW_SLA);
			}
		}

		// Convert due retry-wait tasks to queued.
		tasks.moveRetryWaitToQueued(workflowInstanceId, Instant.now());

		// Ensure roots are queued on first wakeup.
		tasks.markQueuedAttempt0(workflowInstanceId, m.roots());

		// Dispatch all queued tasks (bounded)
		List<JdbcDagTaskInstanceRepository.DispatchableTask> dispatch = tasks
				.selectDispatchableQueued(workflowInstanceId, Instant.now(), 500);
		for (var t : dispatch) {
			try {
				TaskDispatchEnvelope env = new TaskDispatchEnvelope(t.workflowInstanceId(), t.taskInstanceId(),
						t.attempt(), t.tenantId(), t.taskType(), dueTime, t.payloadJson(), t.traceParent());
				String envJson = mapper.writeValueAsString(env);
				readyPublisher.publish(new TaskReadyEvent(workflowInstanceId, triggerId, dueTime, envJson));
				metrics.dagEnqueueCount.add(1);
			} catch (Exception e) {
				metrics.readyPublishError.add(1);
			}
		}
	}

	@Override
	public void onWorkflowSlaDue(long workflowInstanceId, long triggerId, Instant dueTime) {
		try {
			int shardId = shardCalculator.shardForWorkflowInstanceId(workflowInstanceId);
			boolean leader = shardingEnabled ? shardElector.isLeader(shardId) : shardElector.isLeader(0);
			if (!leader) {
				log.debug(
						"checkpoint=master.workflow_sla_ignored_not_leader workflowInstanceId={} triggerId={} dueTime={}",
						workflowInstanceId, triggerId, dueTime);
				return;
			}
			if (wi.isTerminal(workflowInstanceId)) {
				log.info(
						"checkpoint=master.workflow_sla_ignored_terminal workflowInstanceId={} triggerId={} dueTime={}",
						workflowInstanceId, triggerId, dueTime);
				return;
			}
			var key = wi.loadInstanceKey(workflowInstanceId);
			String reason = "Workflow SLA breached";
			boolean transitioned = wi.markWorkflowFailedIfNotTerminal(workflowInstanceId, reason);
			if (transitioned) {
				String eventId = "SLA:" + workflowInstanceId;
				alertPublisher.publish(new AlertEvent(eventId, AlertType.SLA_BREACH, AlertSeverity.CRITICAL,
						key.tenantId(), workflowInstanceId, key.workflowCode(), key.workflowVersion(), null, null, null,
						triggerId, Instant.now(), "Workflow SLA breached", "dueTime=" + dueTime, null));
				log.warn("checkpoint=master.workflow_sla_breached workflowInstanceId={} dueTime={}", workflowInstanceId,
						dueTime);
			}
		} catch (Exception e) {
			log.warn("onWorkflowSlaDue error: {}", e.toString());
		}
	}

	@Override
	public void onTaskState(TaskStateEvent evt) {
		if (evt == null)
			return;
		try {
			long workflowInstanceId = evt.workflowInstanceId();
			int shardId = shardCalculator.shardForWorkflowInstanceId(workflowInstanceId);
			boolean leader = shardingEnabled ? shardElector.isLeader(shardId) : shardElector.isLeader(0);
			if (!leader) {
				log.debug(
						"checkpoint=master.task_state_ignored_not_leader workflowInstanceId={} taskInstanceId={} state={}",
						evt.workflowInstanceId(), evt.taskInstanceId(), evt.state());
				return;
			}
			if (wi.isTerminal(evt.workflowInstanceId())) {
				log.info(
						"checkpoint=master.task_state_ignored_terminal workflowInstanceId={} taskInstanceId={} state={}",
						evt.workflowInstanceId(), evt.taskInstanceId(), evt.state());
				return;
			}

			var key = wi.loadInstanceKey(evt.workflowInstanceId());

			// Ensure we apply state events only to the currently-active attempt for this task instance.
			// Kafka delivery is at-least-once; we may see duplicates or stale events from an earlier attempt.
			var metaOpt = tasks.findTaskMetaByInstanceId(evt.taskInstanceId());
			if (metaOpt.isEmpty())
				return;
			var meta = metaOpt.get();
			if (evt.attempt() != meta.attempt()) {
				log.info(
						"checkpoint=master.task_state_ignored_stale_attempt workflowInstanceId={} taskInstanceId={} evtAttempt={} currentAttempt={} state={}",
						evt.workflowInstanceId(), evt.taskInstanceId(), evt.attempt(), meta.attempt(), evt.state());
				return;
			}

			switch (evt.state()) {
			case "SUCCEEDED", "SKIPPED" -> {
				progression.onTaskTerminalSuccess(evt.workflowInstanceId(), key.tenantId(), key.workflowCode(),
						key.workflowVersion(), meta.taskCode(), /* triggerId */0L, Instant.now());
			}
			case "FAILED" -> {
				retryPlanner.scheduleRetry(evt.workflowInstanceId(), evt.taskInstanceId(),
						evt.errorMessage() == null ? "FAILED" : evt.errorMessage(), Duration.ofSeconds(2),
								Duration.ofMinutes(5));
			}
			case "DLQ" -> {
				String err = evt.errorMessage() == null ? "DLQ" : evt.errorMessage();
				boolean transitioned = wi.markWorkflowFailedIfNotTerminal(evt.workflowInstanceId(), err);
				if (transitioned) {
					String eventId = "WF_FAIL:" + evt.workflowInstanceId();
					alertPublisher.publish(new AlertEvent(eventId, AlertType.WORKFLOW_FAILED, AlertSeverity.CRITICAL,
							key.tenantId(), evt.workflowInstanceId(), key.workflowCode(), key.workflowVersion(),
							evt.taskInstanceId(), meta.taskCode(), evt.attempt(), null, Instant.now(),
							"Workflow failed", err, evt.traceParent()));
				}
			}
			default -> {
				// ignore STARTED etc
			}
			}
		} catch (Exception e) {
			log.warn("onTaskState error: {}", e.toString());
		}
	}
}
