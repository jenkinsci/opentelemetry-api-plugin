/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.api;

import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder;
import io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class ReconfigurableExtendedTracerProviderTest {

    @Test
    void test() {
        ReconfigurableTracerProvider tracerProvider = new ReconfigurableTracerProvider();

        TracerProviderMock tracerProviderImpl_1 = new TracerProviderMock();
        tracerProvider.setDelegate(tracerProviderImpl_1);

        ReconfigurableTracerProvider.ReconfigurableExtendedTracer authenticationTracer =
                (ReconfigurableTracerProvider.ReconfigurableExtendedTracer) tracerProvider
                        .tracerBuilder("io.jenkins.authentication")
                        .setInstrumentationVersion("1.0.0")
                        .build();

        TracerMock authenticationTracerImpl = (TracerMock) authenticationTracer.delegate;
        assertEquals("io.jenkins.authentication", authenticationTracerImpl.instrumentationScopeName);
        assertNull(authenticationTracerImpl.schemaUrl);
        assertEquals("1.0.0", authenticationTracerImpl.instrumentationVersion);
        assertEquals(tracerProviderImpl_1.id, authenticationTracerImpl.tracerProviderId);

        ReconfigurableTracerProvider.ReconfigurableExtendedTracer buildTracer =
                (ReconfigurableTracerProvider.ReconfigurableExtendedTracer) tracerProvider
                        .tracerBuilder("io.jenkins.build")
                        .setSchemaUrl("https://jenkins.io/build")
                        .build();
        TracerMock buildTracerImpl = (TracerMock) buildTracer.delegate;
        assertEquals("io.jenkins.build", buildTracerImpl.instrumentationScopeName);
        assertEquals("https://jenkins.io/build", buildTracerImpl.schemaUrl);
        assertNull(buildTracerImpl.instrumentationVersion);
        assertEquals(tracerProviderImpl_1.id, buildTracerImpl.tracerProviderId);

        ReconfigurableTracerProvider.ReconfigurableExtendedTracer buildTracerShouldBeTheSameInstance =
                (ReconfigurableTracerProvider.ReconfigurableExtendedTracer) tracerProvider
                        .tracerBuilder("io.jenkins.build")
                        .setSchemaUrl("https://jenkins.io/build")
                        .build();

        assertEquals(buildTracer, buildTracerShouldBeTheSameInstance);

        TracerProviderMock tracerProviderImpl_2 = new TracerProviderMock();
        assertNotEquals(tracerProviderImpl_1.id, tracerProviderImpl_2.id);

        // CHANGE THE IMPLEMENTATION OF THE EVENT TRACER PROVIDER
        tracerProvider.setDelegate(tracerProviderImpl_2);

        // VERIFY THE DELEGATE IMPL HAS CHANGED WHILE THE PARAMS REMAINS UNCHANGED
        TracerMock authenticationTracerImpl_2 = (TracerMock) authenticationTracer.delegate;
        assertEquals("io.jenkins.authentication", authenticationTracerImpl_2.instrumentationScopeName);
        assertNull(authenticationTracerImpl_2.schemaUrl);
        assertEquals("1.0.0", authenticationTracerImpl_2.instrumentationVersion);
        assertEquals(tracerProviderImpl_2.id, authenticationTracerImpl_2.tracerProviderId);

        TracerMock buildTracerImpl_2 = (TracerMock) buildTracer.delegate;

        assertEquals("io.jenkins.build", buildTracerImpl_2.instrumentationScopeName);
        assertEquals("https://jenkins.io/build", buildTracerImpl_2.schemaUrl);
        assertNull(buildTracerImpl_2.instrumentationVersion);
        assertEquals(tracerProviderImpl_2.id, buildTracerImpl_2.tracerProviderId);
    }

    static class TracerProviderMock implements TracerProvider {
        static final AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String id;

        public TracerProviderMock() {
            this.id = "TracerProviderMock-" + ID_SOURCE.incrementAndGet();
        }

        @Override
        public TracerBuilder tracerBuilder(String instrumentationScopeName) {
            return new TracerBuilderMock(instrumentationScopeName, id);
        }

        @Override
        public Tracer get(String instrumentationScopeName) {
            return new ExtendedTracerMock(instrumentationScopeName, id);
        }

        @Override
        public Tracer get(String instrumentationScopeName, String instrumentationScopeVersion) {
            return new ExtendedTracerMock(instrumentationScopeName, instrumentationScopeVersion, id);
        }
    }

    static class TracerMock implements Tracer {
        static final AtomicInteger ID_SOURCE = new AtomicInteger(0);

        final String instrumentationScopeName;
        final String tracerProviderId;
        final String id;

        final String schemaUrl;
        final String instrumentationVersion;

        public TracerMock(String instrumentationScopeName, String tracerProviderId) {
            this(instrumentationScopeName, tracerProviderId, null, null);
        }

        public TracerMock(String instrumentationScopeName, String instrumentationVersion, String tracerProviderId) {
            this(instrumentationScopeName, tracerProviderId, null, instrumentationVersion);
        }

        public TracerMock(
                String instrumentationScopeName,
                String tracerProviderId,
                @Nullable String schemaUrl,
                @Nullable String instrumentationVersion) {
            this.id = "TracerMock-" + ID_SOURCE.incrementAndGet();
            this.instrumentationScopeName = instrumentationScopeName;
            this.tracerProviderId = tracerProviderId;
            this.schemaUrl = schemaUrl;
            this.instrumentationVersion = instrumentationVersion;
        }

        @Override
        public SpanBuilder spanBuilder(String spanName) {
            throw new UnsupportedOperationException();
        }
    }

    static class ExtendedTracerMock extends TracerMock implements ExtendedTracer {
        public ExtendedTracerMock(String instrumentationScopeName, String tracerProviderId) {
            super(instrumentationScopeName, tracerProviderId);
        }

        public ExtendedTracerMock(
                String instrumentationScopeName, String instrumentationVersion, String tracerProviderId) {
            super(instrumentationScopeName, instrumentationVersion, tracerProviderId);
        }

        public ExtendedTracerMock(
                String instrumentationScopeName,
                String tracerProviderId,
                @Nullable String schemaUrl,
                @Nullable String instrumentationVersion) {
            super(instrumentationScopeName, tracerProviderId, schemaUrl, instrumentationVersion);
        }

        @Override
        public boolean isEnabled() {
            return ExtendedTracer.super.isEnabled();
        }

        @Override
        public ExtendedSpanBuilder spanBuilder(String spanName) {
            throw new UnsupportedOperationException();
        }
    }

    static class TracerBuilderMock implements TracerBuilder {
        static final AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String id;
        final String instrumentationScopeName;
        final String tracerProviderId;
        String schemaUrl;
        String instrumentationVersion;

        public TracerBuilderMock(String instrumentationScopeName, String tracerProviderId) {
            this.id = "TracerBuilderMock-" + ID_SOURCE.incrementAndGet();
            this.instrumentationScopeName = instrumentationScopeName;
            this.tracerProviderId = tracerProviderId;
        }

        @Override
        public TracerBuilder setSchemaUrl(String schemaUrl) {
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public TracerBuilder setInstrumentationVersion(String instrumentationVersion) {
            this.instrumentationVersion = instrumentationVersion;
            return this;
        }

        @Override
        public Tracer build() {
            return new ExtendedTracerMock(
                    instrumentationScopeName, tracerProviderId, schemaUrl, instrumentationVersion);
        }
    }
}
