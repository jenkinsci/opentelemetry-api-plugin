package io.jenkins.plugins.opentelemetry.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class ReconfigurableOpenTelemetryTest {

    static ReconfigurableOpenTelemetry reconfigurableOpenTelemetry;

    @BeforeAll
    static void beforeClass() {
        reconfigurableOpenTelemetry = ReconfigurableOpenTelemetry.get();
    }

    @Test
    void test_logRecordExporter() {
        Map<String, String> otelConfig = new HashMap<>();
        otelConfig.put("otel.exporter.otlp.endpoint", "http://localhost:4317");
        Resource otelResource = Resource.builder()
                .put("service.name", "ReconfigurableOpenTelemetry test")
                .build();
        reconfigurableOpenTelemetry.configure(otelConfig, otelResource, true);
        LogRecordExporter logRecordExporter = reconfigurableOpenTelemetry.getLogRecordExporter();
        System.out.println(logRecordExporter);
        // reconfigurableOpenTelemetry.testLogRecordExporter();

        otelConfig = new HashMap<>();
        otelConfig.put("otel.exporter.otlp.endpoint", "http://localhost:4317/");
        reconfigurableOpenTelemetry.configure(otelConfig, otelResource, true);
        logRecordExporter = reconfigurableOpenTelemetry.getLogRecordExporter();
        System.out.println(logRecordExporter);
    }

    @Test
    void test_configuration_through_system_properties() {
        System.setProperty("otel.instrumentation.jdbc.enabled", "true");
        System.setProperty("otel.service.name", "jenkins-456");
        try {
            Map<String, String> otelConfig = new HashMap<>();
            otelConfig.put("otel.exporter.otlp.endpoint", "http://localhost:4317");
            Resource otelResource = Resource.builder().build();
            reconfigurableOpenTelemetry.configure(otelConfig, otelResource, true);

            // verify
            assertEquals(
                    Boolean.TRUE,
                    reconfigurableOpenTelemetry.getConfig().getBoolean("otel.instrumentation.jdbc.enabled"));
            assertEquals(
                    "jenkins-456",
                    reconfigurableOpenTelemetry.getResource().getAttribute(ServiceAttributes.SERVICE_NAME));
        } finally {
            System.clearProperty("otel.instrumentation.jdbc.enabled");
        }
    }

    @Test
    void test_configuration_through_passed_properties() {
        try {
            Map<String, String> otelConfig = new HashMap<>();
            otelConfig.put("otel.exporter.otlp.endpoint", "http://localhost:4317");
            otelConfig.put("otel.instrumentation.jdbc.enabled", "true");

            Resource otelResource = Resource.builder()
                    .put(ServiceAttributes.SERVICE_NAME, "jenkins-123")
                    .build();
            reconfigurableOpenTelemetry.configure(otelConfig, otelResource, true);

            // verify
            assertEquals(
                    Boolean.TRUE,
                    reconfigurableOpenTelemetry.getConfig().getBoolean("otel.instrumentation.jdbc.enabled"));
            assertEquals(
                    "jenkins-123",
                    reconfigurableOpenTelemetry.getResource().getAttribute(ServiceAttributes.SERVICE_NAME));
        } finally {
            System.clearProperty("otel.instrumentation.jdbc.enabled");
        }
    }

    @Test
    void test_configuration_through_passed_properties_overwrites() {
        System.setProperty("otel.instrumentation.jdbc.enabled", "true");
        System.setProperty("otel.service.name", "jenkins-456");
        try {
            Map<String, String> otelConfig = new HashMap<>();
            otelConfig.put("otel.exporter.otlp.endpoint", "http://localhost:4317");
            otelConfig.put("otel.instrumentation.jdbc.enabled", "false");

            Resource otelResource = Resource.builder()
                    .put(ServiceAttributes.SERVICE_NAME, "jenkins-123")
                    .build();
            reconfigurableOpenTelemetry.configure(otelConfig, otelResource, true);

            // verify
            assertEquals(
                    Boolean.FALSE,
                    reconfigurableOpenTelemetry.getConfig().getBoolean("otel.instrumentation.jdbc.enabled"));
            assertEquals(
                    "jenkins-123",
                    reconfigurableOpenTelemetry.getResource().getAttribute(ServiceAttributes.SERVICE_NAME));
        } finally {
            System.clearProperty("otel.instrumentation.jdbc.enabled");
        }
    }

        @Test
    void test_configuration_without_endpoint() {
        try {
            Map<String, String> otelConfig = new HashMap<>();
            otelConfig.put("otel.instrumentation.jdbc.enabled", "true");

            Resource otelResource = Resource.builder().put(ServiceAttributes.SERVICE_NAME, "jenkins-123").build();
            reconfigurableOpenTelemetry.configure(otelConfig, otelResource, true);

            // verify
            assertNull(reconfigurableOpenTelemetry.getConfig().getBoolean("otel.instrumentation.jdbc.enabled"));
            assertNull(reconfigurableOpenTelemetry.getResource().getAttribute(ServiceAttributes.SERVICE_NAME));
        } finally {
            System.clearProperty("otel.instrumentation.jdbc.enabled");
        }
    }

    @AfterAll
    static void afterClass() {
        reconfigurableOpenTelemetry.close();
    }
}
