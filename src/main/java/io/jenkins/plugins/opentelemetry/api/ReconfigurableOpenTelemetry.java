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
import io.jenkins.plugins.opentelemetry.api.instrumentation.resource.JenkinsResourceProvider;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.ResourceConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
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
 * <p>
 * With {@value #USE_GLOBAL_OPENTELEMETRY}{@code =true}, and when another component such as the OpenTelemetry Java agent
 * has registered its {@link OpenTelemetry} in {@link GlobalOpenTelemetry}, this class uses that instance instead of
 * building a second OpenTelemetry SDK.
 * </p>
 */
public class ReconfigurableOpenTelemetry implements ExtendedOpenTelemetry, OpenTelemetry, Closeable, ExtensionPoint {

    private static final Logger logger = Logger.getLogger(ReconfigurableOpenTelemetry.class.getName());
    private static final ReconfigurableOpenTelemetry INSTANCE = new ReconfigurableOpenTelemetry();
    private static final AtomicInteger GET_INVOCATION_COUNT = new AtomicInteger(0);
    private static final String NONE = "none";

    /**
     * When {@code true}, use the {@link OpenTelemetry} instance that another component, such as the OpenTelemetry Java
     * agent, registered in {@link GlobalOpenTelemetry}, instead of building a second OpenTelemetry SDK. The Jenkins
     * spans, metrics and logs then go through the same SDK as the agent's instrumentation, so the agent's spans (for
     * example HTTP client calls) are children of the Jenkins spans. Exporters, endpoints, resource and sampler then come
     * from that component, not from the properties passed to {@link #configure(Map, Resource, boolean)}.
     * <p>
     * Off by default. Set it in the configuration properties, as a system property, or as the environment variable
     * {@code OTEL_INSTRUMENTATION_JENKINS_USE_GLOBAL_OPENTELEMETRY}. With the OpenTelemetry Java agent, the agent's
     * {@code opentelemetry-api} instrumentation must be enabled: it is enabled by default, and needs
     * {@code otel.instrumentation.opentelemetry-api.enabled=true} when
     * {@code otel.instrumentation.common.default-enabled=false}.
     * </p>
     */
    public static final String USE_GLOBAL_OPENTELEMETRY = "otel.instrumentation.jenkins.use-global-opentelemetry";

    Resource resource = Resource.empty();
    ConfigProperties config = ConfigPropertiesUtils.emptyConfig();
    OpenTelemetry openTelemetryImpl = OpenTelemetry.noop();
    LogRecordExporter logRecordExporter = NoopLogRecordExporter.getInstance();
    MetricReader metricReader;
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

        // Same precedence as the SDK built below: the passed properties override system properties, which override
        // environment variables
        ConfigProperties requestedConfig = DefaultConfigProperties.create(
                        Collections.emptyMap(),
                        ComponentLoader.forClassLoader(ReconfigurableOpenTelemetry.class.getClassLoader()))
                .withOverrides(openTelemetryProperties);
        if (requestedConfig.getBoolean(USE_GLOBAL_OPENTELEMETRY, false)) {
            Optional<OpenTelemetry> globalOpenTelemetry = getGlobalOpenTelemetryOfAnotherComponent();
            if (globalOpenTelemetry.isPresent()) {
                configureWithGlobalOpenTelemetry(globalOpenTelemetry.get(), requestedConfig, openTelemetryResource);
                return;
            }
            logger.log(
                    Level.WARNING,
                    () -> USE_GLOBAL_OPENTELEMETRY + "=true, but no other component registered an OpenTelemetry "
                            + "instance in GlobalOpenTelemetry. With the OpenTelemetry Java agent, enable its "
                            + "opentelemetry-api instrumentation (otel.instrumentation.opentelemetry-api.enabled=true). "
                            + "Configuring the OpenTelemetry SDK of the plugin instead.");
        }

        // Configure OTel SDK only if an exporter is configured or the endpoint is set.
        var endpoint = openTelemetryProperties.getOrDefault("otel.exporter.otlp.endpoint", NONE);
        if (NONE.equalsIgnoreCase(endpoint)) {
            endpoint = openTelemetryProperties.getOrDefault("otel.exporter.otlp.traces.endpoint", NONE);
        }
        var tracesExporter = openTelemetryProperties.getOrDefault("otel.traces.exporter", NONE);
        var logsExporter = openTelemetryProperties.getOrDefault("otel.logs.exporter", NONE);
        var metricsExporter = openTelemetryProperties.getOrDefault("otel.metrics.exporter", NONE);
        if (!NONE.equalsIgnoreCase(endpoint)
                || !NONE.equalsIgnoreCase(tracesExporter)
                || !NONE.equalsIgnoreCase(logsExporter)
                || !NONE.equalsIgnoreCase(metricsExporter)) {

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
                    .addMetricReaderCustomizer((metricReader, configProperties) -> {
                        // keep a reference to the computed MetricReader for future use in the plugin
                        this.metricReader = metricReader;
                        return metricReader;
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

    /**
     * @return the {@link OpenTelemetry} instance in {@link GlobalOpenTelemetry} if another component registered it,
     * empty if {@link GlobalOpenTelemetry} holds this instance
     */
    private Optional<OpenTelemetry> getGlobalOpenTelemetryOfAnotherComponent() {
        // The constructor calls GlobalOpenTelemetry.set(this). GlobalOpenTelemetry holds another instance if another
        // component set it first, or if it ignores set() as the OpenTelemetry Java agent does. GlobalOpenTelemetry
        // wraps the instance it holds, so compare the tracer providers. A no-op instance does not count: it is also
        // what GlobalOpenTelemetry returns, and keeps, when nothing is set, for example after close().
        TracerProvider globalTracerProvider = GlobalOpenTelemetry.getTracerProvider();
        if (globalTracerProvider == this.traceProviderImpl || globalTracerProvider == TracerProvider.noop()) {
            return Optional.empty();
        }
        return Optional.of(GlobalOpenTelemetry.get());
    }

    private void configureWithGlobalOpenTelemetry(
            OpenTelemetry globalOpenTelemetry, ConfigProperties requestedConfig, Resource openTelemetryResource) {
        setOpenTelemetryImpl(globalOpenTelemetry);
        // The resource and the config as this plugin knows them. The resource that is exported is the one of the
        // component that owns the SDK, which does not share its SDK objects.
        this.config = requestedConfig;
        this.resource = Resource.builder()
                .putAll(new JenkinsResourceProvider().createResource(requestedConfig))
                .putAll(ResourceConfiguration.createEnvironmentResource(requestedConfig))
                .putAll(openTelemetryResource)
                .build();
        this.logRecordExporter = NoopLogRecordExporter.getInstance();
        this.metricReader = null;
        if (shutdownHook != null) {
            // Nothing of this plugin to flush: the component that owns the SDK shuts it down
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null;
        }
        logger.log(
                Level.INFO,
                () -> "OpenTelemetry configured with the GlobalOpenTelemetry instance of another component ("
                        + globalOpenTelemetry.getClass().getName() + "), " + USE_GLOBAL_OPENTELEMETRY
                        + "=true. Its own settings define exporters, endpoints, resource and sampler. "
                        + ConfigPropertiesUtils.prettyPrintOtelSdkConfig(this.config, this.resource));
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

    /**
     * For testing and troubleshooting purpose
     */
    public MetricReader getMetricReader() {
        return metricReader;
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
