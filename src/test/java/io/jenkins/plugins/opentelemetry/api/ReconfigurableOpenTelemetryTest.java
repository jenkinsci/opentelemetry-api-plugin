package io.jenkins.plugins.opentelemetry.api;

import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ReconfigurableOpenTelemetryTest {

    @Test
    public void test() {
        try (ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = new ReconfigurableOpenTelemetry()) {
            Map<String, String> otelConfig = new HashMap<>();
            otelConfig.put("otel.exporter.otlp.endpoint", "http://localhost:4317");
            Resource otelResource = Resource.builder().put("service.name", "ReconfigurableOpenTelemetry test").build();
            reconfigurableOpenTelemetry.configure(otelConfig, otelResource, true);
            LogRecordExporter logRecordExporter = reconfigurableOpenTelemetry.getLogRecordExporter();
            System.out.println(logRecordExporter);
            //reconfigurableOpenTelemetry.testLogRecordExporter();

            otelConfig = new HashMap<>();
            otelConfig.put("otel.exporter.otlp.endpoint", "http://localhost:4317/");
            reconfigurableOpenTelemetry.configure(otelConfig, otelResource, true);
             logRecordExporter = reconfigurableOpenTelemetry.getLogRecordExporter();
            System.out.println(logRecordExporter);
        }
    }

}