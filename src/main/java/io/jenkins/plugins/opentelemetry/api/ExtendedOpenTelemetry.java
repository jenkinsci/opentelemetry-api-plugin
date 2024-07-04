package io.jenkins.plugins.opentelemetry.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.events.EventLoggerBuilder;
import io.opentelemetry.api.incubator.events.EventLoggerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;

import java.util.Map;

public interface ExtendedOpenTelemetry extends ExtensionPoint, OpenTelemetry {
    EventLoggerProvider getEventLoggerProvider();
    EventLoggerBuilder eventLoggerBuilder(String instrumentationScopeName);
    ConfigProperties getConfig();
    Resource getResource();
    void configure(@NonNull Map<String, String> openTelemetryProperties, Resource openTelemetryResource);

    @Deprecated
    OpenTelemetry getImplementation();
}
