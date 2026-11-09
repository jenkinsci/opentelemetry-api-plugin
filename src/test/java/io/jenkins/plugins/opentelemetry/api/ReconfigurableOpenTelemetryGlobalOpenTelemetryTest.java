/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.api;

import static io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry.USE_GLOBAL_OPENTELEMETRY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ReconfigurableOpenTelemetry#USE_GLOBAL_OPENTELEMETRY}: use the OpenTelemetry instance that another component,
 * such as the OpenTelemetry Java agent, registered in {@link GlobalOpenTelemetry}. Here an SDK registered in
 * {@link GlobalOpenTelemetry} before the {@link ReconfigurableOpenTelemetry} plays the other component.
 */
class ReconfigurableOpenTelemetryGlobalOpenTelemetryTest {

    static final Resource RESOURCE = Resource.builder()
            .put(ServiceAttributes.SERVICE_NAME, "jenkins-123")
            .build();
    static final String ENDPOINT = "http://localhost:4317";

    InMemorySpanExporter otherComponentSpans;
    OpenTelemetrySdk otherComponentSdk;
    final List<ReconfigurableOpenTelemetry> created = new ArrayList<>();

    @BeforeAll
    static void beforeAll() {
        // Initialize the class (its static INSTANCE registers itself in GlobalOpenTelemetry) before the tests reset it
        ReconfigurableOpenTelemetry.get();
    }

    @BeforeEach
    void beforeEach() {
        GlobalOpenTelemetry.resetForTest();
        otherComponentSpans = InMemorySpanExporter.create();
        otherComponentSdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(otherComponentSpans))
                        .build())
                .build();
    }

    @AfterEach
    void afterEach() {
        created.forEach(ReconfigurableOpenTelemetry::close);
        otherComponentSdk.close();
        GlobalOpenTelemetry.resetForTest();
        System.clearProperty(USE_GLOBAL_OPENTELEMETRY);
    }

    ReconfigurableOpenTelemetry newReconfigurableOpenTelemetry() {
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = new ReconfigurableOpenTelemetry();
        created.add(reconfigurableOpenTelemetry);
        return reconfigurableOpenTelemetry;
    }

    List<String> otherComponentSpanNames() {
        return otherComponentSpans.getFinishedSpanItems().stream()
                .map(SpanData::getName)
                .collect(Collectors.toList());
    }

    @Test
    void uses_the_global_opentelemetry_of_another_component() {
        GlobalOpenTelemetry.set(otherComponentSdk);
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = newReconfigurableOpenTelemetry();

        reconfigurableOpenTelemetry.configure(
                Map.of(
                        USE_GLOBAL_OPENTELEMETRY,
                        "true",
                        "otel.exporter.otlp.endpoint",
                        ENDPOINT,
                        "otel.instrumentation.jdbc.enabled",
                        "true"),
                RESOURCE,
                true);
        reconfigurableOpenTelemetry
                .getTracer("io.jenkins.test")
                .spanBuilder("jenkins-span")
                .startSpan()
                .end();

        assertEquals(List.of("jenkins-span"), otherComponentSpanNames());
        assertFalse(reconfigurableOpenTelemetry.getImplementation() instanceof OpenTelemetrySdk);
        assertTrue(reconfigurableOpenTelemetry.getConfig().getBoolean("otel.instrumentation.jdbc.enabled"));
        assertEquals(
                "jenkins-123", reconfigurableOpenTelemetry.getResource().getAttribute(ServiceAttributes.SERVICE_NAME));
        assertNull(reconfigurableOpenTelemetry.getMetricReader());
    }

    @Test
    void works_without_an_endpoint_because_the_other_component_exports() {
        GlobalOpenTelemetry.set(otherComponentSdk);
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = newReconfigurableOpenTelemetry();

        reconfigurableOpenTelemetry.configure(Map.of(USE_GLOBAL_OPENTELEMETRY, "true"), RESOURCE, true);
        reconfigurableOpenTelemetry
                .getTracer("io.jenkins.test")
                .spanBuilder("jenkins-span")
                .startSpan()
                .end();

        assertEquals(List.of("jenkins-span"), otherComponentSpanNames());
    }

    @Test
    void close_leaves_the_sdk_of_the_other_component_running() {
        GlobalOpenTelemetry.set(otherComponentSdk);
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = newReconfigurableOpenTelemetry();
        reconfigurableOpenTelemetry.configure(Map.of(USE_GLOBAL_OPENTELEMETRY, "true"), RESOURCE, true);

        reconfigurableOpenTelemetry.close();
        otherComponentSdk
                .getTracer("other")
                .spanBuilder("after-close")
                .startSpan()
                .end();

        assertEquals(List.of("after-close"), otherComponentSpanNames());
    }

    @Test
    void off_by_default() {
        GlobalOpenTelemetry.set(otherComponentSdk);
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = newReconfigurableOpenTelemetry();

        reconfigurableOpenTelemetry.configure(Map.of("otel.exporter.otlp.endpoint", ENDPOINT), RESOURCE, true);
        reconfigurableOpenTelemetry
                .getTracer("io.jenkins.test")
                .spanBuilder("jenkins-span")
                .startSpan()
                .end();

        assertInstanceOf(OpenTelemetrySdk.class, reconfigurableOpenTelemetry.getImplementation());
        assertEquals(List.of(), otherComponentSpanNames());
    }

    @Test
    void enabled_with_a_system_property() {
        System.setProperty(USE_GLOBAL_OPENTELEMETRY, "true");
        GlobalOpenTelemetry.set(otherComponentSdk);
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = newReconfigurableOpenTelemetry();

        reconfigurableOpenTelemetry.configure(Map.of("otel.exporter.otlp.endpoint", ENDPOINT), RESOURCE, true);

        assertFalse(reconfigurableOpenTelemetry.getImplementation() instanceof OpenTelemetrySdk);
    }

    @Test
    void passed_properties_override_the_system_property() {
        System.setProperty(USE_GLOBAL_OPENTELEMETRY, "true");
        GlobalOpenTelemetry.set(otherComponentSdk);
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = newReconfigurableOpenTelemetry();

        reconfigurableOpenTelemetry.configure(
                Map.of(USE_GLOBAL_OPENTELEMETRY, "false", "otel.exporter.otlp.endpoint", ENDPOINT), RESOURCE, true);

        assertInstanceOf(OpenTelemetrySdk.class, reconfigurableOpenTelemetry.getImplementation());
    }

    @Test
    void falls_back_to_its_own_sdk_when_no_other_component_registered_one() {
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = newReconfigurableOpenTelemetry();

        reconfigurableOpenTelemetry.configure(
                Map.of(USE_GLOBAL_OPENTELEMETRY, "true", "otel.exporter.otlp.endpoint", ENDPOINT), RESOURCE, true);

        assertInstanceOf(OpenTelemetrySdk.class, reconfigurableOpenTelemetry.getImplementation());
        assertSame(reconfigurableOpenTelemetry.getTracerProvider(), GlobalOpenTelemetry.getTracerProvider());
    }

    @Test
    void falls_back_to_its_own_sdk_when_the_global_opentelemetry_is_noop() {
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = newReconfigurableOpenTelemetry();

        reconfigurableOpenTelemetry.configure(
                Map.of(USE_GLOBAL_OPENTELEMETRY, "true", "otel.exporter.otlp.endpoint", ENDPOINT), RESOURCE, true);

        assertInstanceOf(OpenTelemetrySdk.class, reconfigurableOpenTelemetry.getImplementation());
    }

    @Test
    void switches_between_its_own_sdk_and_the_global_opentelemetry_at_runtime() {
        GlobalOpenTelemetry.set(otherComponentSdk);
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = newReconfigurableOpenTelemetry();
        Map<String, String> ownSdk = Map.of("otel.exporter.otlp.endpoint", ENDPOINT);
        Map<String, String> global = Map.of(USE_GLOBAL_OPENTELEMETRY, "true", "otel.exporter.otlp.endpoint", ENDPOINT);

        reconfigurableOpenTelemetry.configure(ownSdk, RESOURCE, true);
        OpenTelemetry first = reconfigurableOpenTelemetry.getImplementation();
        reconfigurableOpenTelemetry.configure(global, RESOURCE, true);
        // A tracer obtained before the switch follows it
        reconfigurableOpenTelemetry
                .getTracer("io.jenkins.test")
                .spanBuilder("jenkins-span")
                .startSpan()
                .end();
        reconfigurableOpenTelemetry.configure(ownSdk, RESOURCE, true);

        assertInstanceOf(OpenTelemetrySdk.class, first);
        assertEquals(List.of("jenkins-span"), otherComponentSpanNames());
        assertInstanceOf(OpenTelemetrySdk.class, reconfigurableOpenTelemetry.getImplementation());
    }
}
