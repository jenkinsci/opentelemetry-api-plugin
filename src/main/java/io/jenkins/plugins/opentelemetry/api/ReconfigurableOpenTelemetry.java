/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.api;

import com.google.common.base.Function;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.PreDestroy;
import org.apache.commons.lang.StringUtils;

/**
 * <p>
 * Reconfigurable {@link OpenTelemetry}.
 * </p>
 * <p>
 * We need reconfigurability because Jenkins supports changing the configuration of the OpenTelemetry params at runtime.
 * All instantiated tracers, loggers, and eventLoggers are reconfigured when the configuration changes, when
 * {@link ReconfigurableOpenTelemetry#configure(Map, Resource)} is invoked.
 * </p>
 * <p>
 * Jenkins components interested in being notified after the OpenTelemetry configuration changes can be marked as @{@link Extension}
 * and implement {@link OpenTelemetryLifecycleListener}.
 * </p>
 */
public class ReconfigurableOpenTelemetry implements ExtendedOpenTelemetry, OpenTelemetry, Closeable, ExtensionPoint {

    private static final Logger logger = Logger.getLogger(ReconfigurableOpenTelemetry.class.getName());
    private static final ReconfigurableOpenTelemetry INSTANCE = new ReconfigurableOpenTelemetry();
    private static final AtomicInteger GET_INVOCATION_COUNT = new AtomicInteger(0);

    Resource resource = Resource.empty();
    ConfigProperties config = ConfigPropertiesUtils.emptyConfig();
    OpenTelemetry openTelemetryImpl = OpenTelemetry.noop();
    LogRecordExporter logRecordExporter = NoopLogRecordExporter.getInstance();
    Thread shutdownHook;
    final ReconfigurableMeterProvider meterProviderImpl = new ReconfigurableMeterProvider();
    final ReconfigurableTracerProvider traceProviderImpl = new ReconfigurableTracerProvider();
    final ReconfigurableLoggerProvider loggerProviderImpl = new ReconfigurableLoggerProvider();

    /*
     * Ensures this class is loaded and the static singleton `INSTANCE` is instantiated.
     */
    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.SYSTEM_CONFIG_LOADED)
    public static void init() {
        logger.log(Level.FINE, () -> "OpenTelemetry configured as NoOp: " + INSTANCE);
    }

    /**
     * Use a factory method for the @{@link Extension} to ensure single instantiation
     * across Jenkins
     * <p>
     * The Jenkins component {@link ReconfigurableOpenTelemetry} is instantiated through the static factory method
     * {@link #get()} rather than through the instance constructor to ensure that we have single
     * instantiation across Jenkins' @{@link Extension} and Google Guice @{@link com.google.inject.Inject}.
     * </p>
     * <p>
     * This factory method works in conjunction with {@link OpenTelemetryApiGuiceModule}
     * </p>
     */
    @Extension(ordinal = Integer.MAX_VALUE)
    public static ReconfigurableOpenTelemetry get() {
        int getInvocationCount = GET_INVOCATION_COUNT.incrementAndGet();
        logger.log(Level.FINE, () -> "get(invocationCount=" + getInvocationCount + "): " + INSTANCE);
        return INSTANCE;
    }

    /**
     * <p>
     * Initialize as NoOp.
     * </p>
     *
     * @see #get()
     */
    public ReconfigurableOpenTelemetry() {
        try {
            GlobalOpenTelemetry.set(this);
        } catch (IllegalStateException e) {
            logger.log(Level.WARNING, "GlobalOpenTelemetry already set", e);
        }

        logger.log(
                Level.FINE,
                () -> "Configure " + "GlobalOpenTelemetry with instance "
                        + Optional.of(GlobalOpenTelemetry.get()).map(ot -> ot + "@" + System.identityHashCode(ot)));
    }

    /**
     * Configure the OpenTelemetry SDK with the given properties and resource disabling the OTel SDK shutdown hook
     *
     * @deprecated use {@link #configure(Map, Resource, boolean)} instead
     */
    @Deprecated
    @Override
    public void configure(@NonNull Map<String, String> openTelemetryProperties, Resource openTelemetryResource) {
        configure(openTelemetryProperties, openTelemetryResource, true);
    }

    @Override
    public void configure(
            @NonNull Map<String, String> openTelemetryProperties,
            Resource openTelemetryResource,
            boolean disableShutdownHook) {

        // Configure real OTel SDK if one of the following config is passed,
        // otherwise setup as no-op:
        // 1. `otel.exporter.otlp.endpoint` is defined
        //    `otel.[traces,logs,metrics].exporter` are `otlp` by default if they are not defined.
        // 2. `otel.exporter.otlp.endpoint` not defined but exporters are defined and they are not `otlp`
        var endpoint = openTelemetryProperties.get("otel.exporter.otlp.endpoint");
        if (StringUtils.isBlank(endpoint)) {
            endpoint = openTelemetryProperties.get("otel.exporter.otlp.traces.endpoint");
        }
        var tracesExporter = openTelemetryProperties.get("otel.traces.exporter");
        var logsExporter = openTelemetryProperties.get("otel.logs.exporter");
        var metricsExporter = openTelemetryProperties.get("otel.metrics.exporter");
        if (StringUtils.isNotBlank(endpoint)
                || (StringUtils.isNotBlank(tracesExporter) && !tracesExporter.equalsIgnoreCase("otlp"))
                || (StringUtils.isNotBlank(logsExporter) && !logsExporter.equalsIgnoreCase("otlp"))
                || (StringUtils.isNotBlank(metricsExporter) && !metricsExporter.equalsIgnoreCase("otlp"))) {

            logger.log(Level.FINE, "initializeOtlp");

            // OPENTELEMETRY SDK
            OpenTelemetrySdk openTelemetrySdk = AutoConfiguredOpenTelemetrySdk.builder()
                    // properties
                    .addPropertiesCustomizer((Function<ConfigProperties, Map<String, String>>) configProperties -> {
                        // Overwrite OTel SDK Properties loaded through Environment variables and `-Dotel.*` system
                        // properties by properties passed through the Jenkins OTel Plugin config GUI
                        if (logger.isLoggable(Level.INFO)) {
                            for (Map.Entry<String, String> keyValue : openTelemetryProperties.entrySet()) {
                                if (configProperties.getString(keyValue.getKey()) != null) {
                                    logger.log(
                                            Level.INFO,
                                            "Overwrite OTel SDK property: " + keyValue.getKey() + "="
                                                    + configProperties.getString(keyValue.getKey())
                                                    + " with Jenkins Plugin property: " + keyValue.getValue());
                                }
                            }
                        }
                        return openTelemetryProperties;
                    })
                    .addPropertiesCustomizer((Function<ConfigProperties, Map<String, String>>) configProperties -> {
                        // keep a reference to the computed config properties for future use in the plugin
                        this.config = configProperties;
                        return Collections.emptyMap();
                    })
                    // resource
                    .addResourceCustomizer((resource1, configProperties) -> {
                        // keep a reference to the computed Resource for future use in the plugin
                        this.resource = Resource.builder()
                                .putAll(resource1)
                                .putAll(openTelemetryResource)
                                .build();
                        return this.resource;
                    })
                    .addLogRecordExporterCustomizer((logRecordExporter, configProperties) -> {
                        // keep a reference to the computed LogRecordExporter for future use in the plugin
                        this.logRecordExporter = logRecordExporter;
                        return logRecordExporter;
                    })
                    .disableShutdownHook()
                    .build()
                    .getOpenTelemetrySdk();
            setOpenTelemetryImpl(openTelemetrySdk);

            if (disableShutdownHook) {
                if (shutdownHook == null) {
                    // nothing to do, no shutdown hook registered
                } else {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                }
            } else {
                if (shutdownHook == null) {
                    shutdownHook = new Thread(this::close, "Reconfigurable OpenTelemetry SDK Shutdown Hook");
                    Runtime.getRuntime().addShutdownHook(shutdownHook);
                } else {
                    // nothing to do, shutdown hook already registered
                }
            }

            logger.log(
                    Level.INFO,
                    () -> "OpenTelemetry configured: "
                            + ConfigPropertiesUtils.prettyPrintOtelSdkConfig(this.config, this.resource));

        } else { // NO-OP

            this.resource = Resource.empty();
            this.config = ConfigPropertiesUtils.emptyConfig();
            setOpenTelemetryImpl(OpenTelemetry.noop());

            this.logRecordExporter = NoopLogRecordExporter.getInstance();

            logger.log(Level.FINE, () -> "OpenTelemetry configured as NoOp");
        }

        postOpenTelemetrySdkConfiguration();
    }

    protected void setOpenTelemetryImpl(OpenTelemetry openTelemetryImpl) {
        if (this.openTelemetryImpl instanceof OpenTelemetrySdk) {
            logger.log(Level.FINE, () -> "Shutdown OTel SDK...");
            CompletableResultCode shutdown = ((OpenTelemetrySdk) this.openTelemetryImpl).shutdown();
            if (!shutdown.join(1, TimeUnit.SECONDS).isSuccess()) {
                logger.log(Level.WARNING, "Failure to shutdown OTel SDK");
            }
        }
        this.openTelemetryImpl = openTelemetryImpl;
        this.meterProviderImpl.setDelegate(openTelemetryImpl.getMeterProvider());
        this.traceProviderImpl.setDelegate(openTelemetryImpl.getTracerProvider());
        this.loggerProviderImpl.setDelegate(openTelemetryImpl.getLogsBridge());
    }

    @PreDestroy
    @Override
    public void close() {
        logger.log(Level.FINE, "Shutdown...");
        // OTEL SDK
        if (this.openTelemetryImpl instanceof OpenTelemetrySdk) {
            logger.log(Level.FINE, () -> "Shutdown OTel SDK...");
            CompletableResultCode shutdown = ((OpenTelemetrySdk) this.openTelemetryImpl).shutdown();
            if (!shutdown.join(1, TimeUnit.SECONDS).isSuccess()) {
                logger.log(Level.WARNING, "Failure to shutdown OTel SDK");
            }
        }
        GlobalOpenTelemetry.resetForTest();
    }

    @Override
    public TracerProvider getTracerProvider() {
        return traceProviderImpl;
    }

    @Override
    public Tracer getTracer(String instrumentationScopeName) {
        return traceProviderImpl.get(instrumentationScopeName);
    }

    @Override
    public Tracer getTracer(String instrumentationScopeName, String instrumentationScopeVersion) {
        return traceProviderImpl.get(instrumentationScopeName, instrumentationScopeVersion);
    }

    @Override
    public TracerBuilder tracerBuilder(String instrumentationScopeName) {
        return traceProviderImpl.tracerBuilder(instrumentationScopeName);
    }

    @Override
    public MeterProvider getMeterProvider() {
        return meterProviderImpl;
    }

    @Override
    public Meter getMeter(String instrumentationScopeName) {
        return meterProviderImpl.get(instrumentationScopeName);
    }

    @Override
    public MeterBuilder meterBuilder(String instrumentationScopeName) {
        return meterProviderImpl.meterBuilder(instrumentationScopeName);
    }

    @Deprecated
    @Override
    public OpenTelemetry getImplementation() {
        return openTelemetryImpl;
    }

    @Override
    @NonNull
    public Resource getResource() {
        return resource;
    }

    @Override
    @NonNull
    public ConfigProperties getConfig() {
        return config;
    }

    @Override
    public LoggerProvider getLogsBridge() {
        return loggerProviderImpl;
    }

    @Override
    public ContextPropagators getPropagators() {
        return openTelemetryImpl.getPropagators();
    }

    /**
     * For testing and troubleshooting purpose
     */
    public LogRecordExporter getLogRecordExporter() {
        return logRecordExporter;
    }

    @OverridingMethodsMustInvokeSuper
    protected void postOpenTelemetrySdkConfiguration() {
        ExtensionList.lookup(OpenTelemetryLifecycleListener.class).stream()
                .sorted()
                .forEach(openTelemetryLifecycleListener -> {
                    logger.log(
                            Level.FINE,
                            () -> "Notify " + openTelemetryLifecycleListener + " after OpenTelemetry configuration");
                    openTelemetryLifecycleListener.afterConfiguration(this.config);
                });
    }

    /**
     * Noop implementation of {@link LogRecordExporter}
     */
    private static class NoopLogRecordExporter implements LogRecordExporter {
        static final NoopLogRecordExporter INSTANCE = new NoopLogRecordExporter();

        static NoopLogRecordExporter getInstance() {
            return INSTANCE;
        }

        private NoopLogRecordExporter() {}

        @Override
        public CompletableResultCode export(Collection<LogRecordData> logs) {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public String toString() {
            return "NoopLogRecordExporter";
        }
    }
}
