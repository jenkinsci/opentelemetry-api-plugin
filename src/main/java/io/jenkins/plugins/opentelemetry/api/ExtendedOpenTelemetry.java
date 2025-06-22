package io.jenkins.plugins.opentelemetry.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Extension of {@link OpenTelemetry} that provides additional functionality:
 * <ul>
 *     <li>Read access top the {@link ConfigProperties} and {@link Resource}</li>
 *     <li>Ability to be reconfigured through {@link #configure(Map, Resource, boolean)}</li>
 *     <li>Ability to be used as a Jenkins {@link hudson.Extension}</li>
 * </ul>
 */
public interface ExtendedOpenTelemetry extends ExtensionPoint, OpenTelemetry {

    /**
     * {@link ConfigProperties} used to instantiate this OpenTelemetry instance using the {@link io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk}.
     */
    ConfigProperties getConfig();

    /**
     * {@link Resource} used by this OpenTelemetry instance for the resource attributes of the produced telemetry
     */
    Resource getResource();

    /**
     * @deprecated use {@link #configure(Map, Resource, boolean)}
     */
    @Deprecated
    void configure(@NonNull Map<String, String> openTelemetryProperties, Resource openTelemetryResource);

    /**
     * Reconfigures the {@link OpenTelemetry} instance. If no exporter is explicitly defined, this OpenTelemetry instance is NoOp.
     *
     * @param openTelemetryProperties properties used as {@link ConfigProperties} through {@link io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder#addPropertiesSupplier(Supplier)}
     * @param openTelemetryResource   resource attributes passed through {@link io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder#addResourceCustomizer(BiFunction)}
     * @param disableShutdownHook     enable / disable a shutdown hook
     */
    default void configure(
            @NonNull Map<String, String> openTelemetryProperties,
            Resource openTelemetryResource,
            boolean disableShutdownHook) {}

    @Deprecated
    OpenTelemetry getImplementation();
}
