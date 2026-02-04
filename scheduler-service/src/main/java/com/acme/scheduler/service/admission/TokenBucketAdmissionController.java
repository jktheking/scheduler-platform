package com.acme.scheduler.service.admission;

import com.acme.scheduler.common.concurrent.TokenBucket;
import com.acme.scheduler.service.port.AdmissionController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tenant token bucket admission controller.
 *
 * This covers RATE limiting.
 * Backpressure is handled by higher-level controllers (InflightLimiter, KafkaAwareAdmissionController).
 */
public final class TokenBucketAdmissionController implements AdmissionController {

  public record Config(
      long refillTokensPerSecond,
      long capacity,
      int maxTenants
  ) {}

  private final Config cfg;
  private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

  public TokenBucketAdmissionController(Config cfg) {
    this.cfg = cfg;
  }

  @Override
  public Decision decide(String tenantId, String operation) {
    String key = tenantId == null ? "default" : tenantId;
    TokenBucket bucket = buckets.computeIfAbsent(key, k -> {
      if (buckets.size() >= cfg.maxTenants()) {
        // Avoid unbounded growth; collapse into default bucket.
        return buckets.computeIfAbsent("default",
            kk -> new TokenBucket(cfg.refillTokensPerSecond(), cfg.capacity()));
      }
      return new TokenBucket(cfg.refillTokensPerSecond(), cfg.capacity());
    });

    if (!bucket.tryConsume(1)) {
      return Decision.rejectRate("token bucket empty");
    }
    return Decision.allow();
  }
}
