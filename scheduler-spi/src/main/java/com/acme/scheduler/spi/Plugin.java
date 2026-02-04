package com.acme.scheduler.spi;

/**
 * Minimal SPI marker. We'll expand this into typed SPIs + PluginLoader + descriptors in later steps.
 */
public interface Plugin {
 String pluginId();
 default void start() {}
 default void stop() {}
}
