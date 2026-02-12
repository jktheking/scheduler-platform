package com.acme.scheduler.service.admission;

import com.acme.scheduler.service.port.AdmissionController.DecisionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketAdmissionControllerTest {

  private static boolean isAllowed(TokenBucketAdmissionController ctrl, String tenant, String op) {
    return ctrl.decide(tenant, op).type() == DecisionType.ALLOW;
  }

  @Test
  void enforces_capacity_and_rejects_when_empty() {
    // TokenBucket requires refillTokensPerSecond > 0. We keep it low (1 token/sec) and
    // run the consumes back-to-back so the test remains deterministic (no time to refill).
    var ctrl = new TokenBucketAdmissionController(new TokenBucketAdmissionController.Config(
        1L,
        2L,
        100
    ));

    assertThat(isAllowed(ctrl, "t1", "ingest")).isTrue();
    assertThat(isAllowed(ctrl, "t1", "ingest")).isTrue();

    var d = ctrl.decide("t1", "ingest");
    // On exhaustion, controller must reject (exact reason text is not a contract).
    assertThat(d.type()).isNotEqualTo(DecisionType.ALLOW);
  }

  @Test
  void caps_tenant_buckets_to_maxTenants_and_falls_back_to_default_bucket() {
    // TokenBucket requires refillTokensPerSecond > 0. Keep it low and execute back-to-back.
    var ctrl = new TokenBucketAdmissionController(new TokenBucketAdmissionController.Config(
        1L,
        1L,
        1 // only 1 tenant bucket allowed
    ));

    // First tenant gets its own bucket.
    assertThat(isAllowed(ctrl, "tenant1", "ingest")).isTrue();

    // Second tenant should collapse into default bucket and consume it.
    assertThat(isAllowed(ctrl, "tenant2", "ingest")).isTrue();

    // Default bucket is now empty -> reject.
    var d = ctrl.decide(null, "ingest");
    assertThat(d.type()).isNotEqualTo(DecisionType.ALLOW);
  }
}
