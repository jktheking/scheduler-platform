package com.acme.scheduler.master.observability;

import com.acme.scheduler.meter.SchedulerMeter;

/**
 * Metrics for scheduler-master.
 *
 * <p>IMPORTANT: master should not depend directly on OpenTelemetry types.
 * We use the SchedulerMeter facade (implemented in :scheduler-meter) so that
 * core/master code remains library-agnostic.
 */
public final class MasterMetrics {
  public final SchedulerMeter.Counter commandClaimed;        // db-poll path
  public final SchedulerMeter.Counter commandProcessed;      // db-poll path
  public final SchedulerMeter.Counter commandKafkaConsumed;  // kafka consumer path
  public final SchedulerMeter.Counter commandKafkaError;
  public final SchedulerMeter.Counter workflowScheduled;

  public final SchedulerMeter.Counter triggerClaimed;
  public final SchedulerMeter.Counter triggerProcessed;
  public final SchedulerMeter.Counter triggerError;

  public final SchedulerMeter.Counter readyPublished;
  public final SchedulerMeter.Counter readyPublishError;

  public final SchedulerMeter.Counter dagMaterializeCount;
  public final SchedulerMeter.Counter dagEnqueueCount;
  public final SchedulerMeter.Counter dagProgressUnblocked;
  public final SchedulerMeter.Counter retryScheduled;
  public final SchedulerMeter.Counter dlqCreated;

  public MasterMetrics(SchedulerMeter meter) {
    this.commandClaimed = meter.counter("scheduler.master.command.claimed", "Commands claimed from DB queue");
    this.commandProcessed = meter.counter("scheduler.master.command.processed", "Commands processed from DB queue");
    this.commandKafkaConsumed = meter.counter("scheduler.master.command.kafka.consumed", "Commands consumed from Kafka");
    this.commandKafkaError = meter.counter("scheduler.master.command.kafka.error", "Kafka consume errors");

    this.workflowScheduled = meter.counter("scheduler.master.workflow.scheduled", "Workflows scheduled by master");

    this.triggerClaimed = meter.counter("scheduler.master.trigger.claimed", "Triggers claimed");
    this.triggerProcessed = meter.counter("scheduler.master.trigger.processed", "Triggers processed");
    this.triggerError = meter.counter("scheduler.master.trigger.error", "Trigger processing errors");

    this.readyPublished = meter.counter("scheduler.master.ready.published", "Ready events published");
    this.readyPublishError = meter.counter("scheduler.master.ready.publish.error", "Ready publish errors");

    this.dagMaterializeCount = meter.counter("scheduler.master.dag.materialize.count", "DAG materializations");
    this.dagEnqueueCount = meter.counter("scheduler.master.dag.enqueue.count", "DAG task enqueues");
    this.dagProgressUnblocked = meter.counter("scheduler.master.dag.progress.unblocked.count", "Tasks unblocked by DAG progression");
    this.retryScheduled = meter.counter("scheduler.master.retry.scheduled.count", "Retries scheduled");
    this.dlqCreated = meter.counter("scheduler.master.dlq.created.count", "DLQ tasks created");
  }
}
