plugins { `java-library` }

dependencies {
 api(libs.otel.api)
 api(libs.otel.sdk)
 api(libs.otel.sdk.trace)
 api(libs.otel.exporter.otlp)
}
