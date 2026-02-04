package com.acme.scheduler.meter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;

/**
 * Minimal placeholder; later we'll wire real SDK config, resource attrs, propagators, metrics + logs.
 */
public final class Otel {
 private Otel() {}

 public static OpenTelemetry initNoop() {
 OpenTelemetry otel = OpenTelemetrySdk.builder().build();
 GlobalOpenTelemetry.set(otel);
 return otel;
 }
}
