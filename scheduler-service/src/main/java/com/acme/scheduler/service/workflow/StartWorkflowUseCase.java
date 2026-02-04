package com.acme.scheduler.service.workflow;

import com.acme.scheduler.service.admission.KafkaAwareAdmissionController;
import com.acme.scheduler.service.port.AdmissionController;
import com.acme.scheduler.service.port.CommandIngestionGateway;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StartWorkflowUseCase {

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
    if (decision.type() != AdmissionController.DecisionType.ALLOW) {
      return new Response(false, null, decision.type().name(), decision.reason());
    }

    String commandId = UUID.randomUUID().toString();
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
      CommandIngestionGateway.Result r = gateway.ingest(cmd);
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
