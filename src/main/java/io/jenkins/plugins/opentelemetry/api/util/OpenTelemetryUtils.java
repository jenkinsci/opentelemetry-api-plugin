package io.jenkins.plugins.opentelemetry.api.util;

import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.jenkins.plugins.opentelemetry.api.logs.TestLogRecordData;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility methods for working with OpenTelemetry.
 */
public class OpenTelemetryUtils {
    private final static Logger logger = Logger.getLogger(OpenTelemetryUtils.class.getName());

    /**
     * <p>
     * Check if the OpenTelemetry instrumentation for the given instrumentation name is enabled.
     * </p>
     * <p>
     * Search for system properties and environment variables.
     * </p>
     * <p>
     * See <a href="https://opentelemetry.io/docs/zero-code/java/agent/configuration/#suppressing-specific-agent-instrumentation">
     * Docs - Zero-code Instrumentation - Java - Agent - Configuration</a>
     * </p>
     *
     * @param instrumentationName the name of the instrumentation like "jdbc", "web"...
     */
    public static boolean isOtelInstrumentationEnabled(String instrumentationName) {
        boolean defaultEnabled = getProperty("otel.instrumentation.common.default-enabled", true);
        return getProperty("otel.instrumentation." + instrumentationName + ".enabled", defaultEnabled);
    }

    /**
     * Get a boolean property from the system properties or environment variables.
     *
     * @param propertyName the name of the property like "otel.instrumentation.jdbc.enabled"
     * @param defaultValue the default value if the property is not set
     * @return the value of the property if set or the default value
     */
    static boolean getProperty(String propertyName, boolean defaultValue) {
        String systemPropertyValue = System.getProperty(propertyName);
        if (systemPropertyValue != null) {
            return "true".equalsIgnoreCase(systemPropertyValue);
        }
        String envVar = propertyName.replace('.', '_').replace('-', '_').toUpperCase();
        String envVarValue = System.getenv(envVar);
        if (envVarValue != null) {
            return "true".equalsIgnoreCase(envVarValue);
        }
        return defaultValue;
    }

    static public String testLogRecordExporter() {
        ReconfigurableOpenTelemetry openTelemetry = ReconfigurableOpenTelemetry.get();
        LogRecordData logRecordData = TestLogRecordData.builder()
                .setTimestamp(Instant.now())
                .setResource(openTelemetry.getResource())
                .setSeverityText(Severity.INFO.name())
                .setSeverity(Severity.INFO)
                .setInstrumentationScopeInfo(InstrumentationScopeInfo.create("io.jenkins.opentelemetry.api"))
                .setBody("Test log record")
                .build();
        CompletableResultCode result = openTelemetry.getLogRecordExporter().export(Collections.singleton(logRecordData));
        result.join(1, TimeUnit.SECONDS);
        String resultMessage = "testLogRecordExporter(): result(success: " + result.isSuccess() + "done: " + result.isDone() + "), " + openTelemetry.getLogRecordExporter() + ", " + logRecordData + " -";
        logger.log(Level.INFO, resultMessage);
        return resultMessage;
    }

    private OpenTelemetryUtils() {
    }
}
