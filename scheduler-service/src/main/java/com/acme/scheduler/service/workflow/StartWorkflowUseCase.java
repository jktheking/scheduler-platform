package com.acme.scheduler.service.workflow;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.acme.scheduler.service.admission.KafkaAwareAdmissionController;
import com.acme.scheduler.service.port.AdmissionController;
import com.acme.scheduler.service.port.CommandIngestionGateway;

public final class StartWorkflowUseCase {

  private static final Logger log = LoggerFactory.getLogger(StartWorkflowUseCase.class);

  public record Request(
      String tenantId,
      long workflowCode,
      int workflowVersion,
      String idempotencyKey,
      String payloadJson
  ) {}

  public record Response(
      boolean accepted,
      String commandId,
      String rejectionType,  // RATE_LIMIT | BACKPRESSURE | DUPLICATE | NONE
      String message
  ) {}

  private final AdmissionController admission;
  private final CommandIngestionGateway gateway;

  public StartWorkflowUseCase(AdmissionController admission, CommandIngestionGateway gateway) {
    this.admission = Objects.requireNonNull(admission);
    this.gateway = Objects.requireNonNull(gateway);
  }

  public Response execute(Request req) {
    var decision = admission.decide(req.tenantId(), "workflow.start");
    log.info("checkpoint=ingest.admission tenantId={} workflowCode={} workflowVersion={} decisionType={} reason={}",
        req.tenantId(), req.workflowCode(), req.workflowVersion(), decision.type(), decision.reason());
    if (decision.type() != AdmissionController.DecisionType.ALLOW) {
      return new Response(false, null, decision.type().name(), decision.reason());
    }

    String commandId = UUID.randomUUID().toString();
    log.info("checkpoint=ingest.command_created tenantId={} workflowCode={} workflowVersion={} idempotencyKey={} commandId={}",
        req.tenantId(), req.workflowCode(), req.workflowVersion(), req.idempotencyKey(), commandId);
    var cmd = new CommandEnvelope(
        req.tenantId(),
        commandId,
        req.idempotencyKey(),
        "START_PROCESS",
        req.workflowCode(),
        req.workflowVersion(),
        Instant.now(),
        req.payloadJson() == null ? "{}" : req.payloadJson()
    );

    try {
      log.info("checkpoint=ingest.publish_begin tenantId={} workflowCode={} workflowVersion={} commandId={}",
          req.tenantId(), req.workflowCode(), req.workflowVersion(), commandId);
      CommandIngestionGateway.Result r = gateway.ingest(cmd);
      log.info("checkpoint=ingest.publish_end tenantId={} workflowCode={} workflowVersion={} commandId={} resultType={} message={}",
          req.tenantId(), req.workflowCode(), req.workflowVersion(), commandId, r.type(), r.message());
      return switch (r.type()) {
        case ACCEPTED -> new Response(true, commandId, "NONE", r.message());
        case DUPLICATE -> new Response(true, null, "DUPLICATE", r.message());
        case REJECTED -> new Response(false, null, "BACKPRESSURE", r.message());
      };
    } finally {
      // If admission is KafkaAwareAdmissionController, release its inflight permit.
      if (admission instanceof KafkaAwareAdmissionController ka) {
        ka.release();
      }
    }
  }
}