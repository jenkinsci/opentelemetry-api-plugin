package io.jenkins.plugins.opentelemetry.api.util;

/**
 * Utility methods for working with OpenTelemetry.
 */
public class OpenTelemetryUtils {

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


    private OpenTelemetryUtils() {
    }
}
