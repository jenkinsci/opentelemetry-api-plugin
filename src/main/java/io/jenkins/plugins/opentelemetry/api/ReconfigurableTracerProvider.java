/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.api;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder;
import io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nonnull;

/**
 * <p>
 * A {@link TracerProvider} that allows to reconfigure the {@link Tracer}s.
 * </p>
 * <p>
 * We need reconfigurability because Jenkins supports changing the configuration of the OpenTelemetry params at runtime.
 * All instantiated tracers are reconfigured when the configuration changes, when
 * {@link ReconfigurableTracerProvider#setDelegate(TracerProvider)} is invoked.
 * </p>
 * <p>
 *     IMPORTANT: requires the OpenTelemetry API incubator module to be on the classpath for provided
 *     {@link TracerProvider} to create {@link ExtendedTracer}s.
 * </p>
 */
class ReconfigurableTracerProvider implements TracerProvider {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private TracerProvider delegate;

    private final ConcurrentMap<InstrumentationScope, ReconfigurableExtendedTracer> tracers = new ConcurrentHashMap<>();

    public ReconfigurableTracerProvider() {
        this(TracerProvider.noop());
    }

    public ReconfigurableTracerProvider(TracerProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Tracer get(String instrumentationScopeName) {
        lock.readLock().lock();
        try {
            return tracers.computeIfAbsent(
                    new InstrumentationScope(instrumentationScopeName),
                    instrumentationScope -> new ReconfigurableExtendedTracer(
                            delegate.get(instrumentationScope.instrumentationScopeName), lock));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDelegate(TracerProvider delegate) {
        lock.writeLock().lock();
        try {
            this.delegate = delegate;
            tracers.forEach((instrumentationScope, reconfigurableExtendedTracer) -> {
                TracerBuilder tracerBuilder = delegate.tracerBuilder(instrumentationScope.instrumentationScopeName);
                Optional.ofNullable(instrumentationScope.instrumentationScopeVersion)
                        .ifPresent(tracerBuilder::setInstrumentationVersion);
                Optional.ofNullable(instrumentationScope.schemaUrl).ifPresent(tracerBuilder::setSchemaUrl);
                reconfigurableExtendedTracer.setDelegate(tracerBuilder.build());
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Tracer get(String instrumentationScopeName, String instrumentationScopeVersion) {
        lock.readLock().lock();
        try {
            return tracers.computeIfAbsent(
                    new InstrumentationScope(instrumentationScopeName, null, instrumentationScopeVersion),
                    instrumentationScope -> new ReconfigurableExtendedTracer(
                            delegate.get(instrumentationScopeName, instrumentationScopeVersion), lock));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public TracerBuilder tracerBuilder(String instrumentationScopeName) {
        lock.readLock().lock();
        try {
            return new ReconfigurableTracerBuilder(
                    delegate.tracerBuilder(instrumentationScopeName), instrumentationScopeName, lock);
        } finally {
            lock.readLock().unlock();
        }
    }

    public TracerProvider getDelegate() {
        lock.readLock().lock();
        try {
            return delegate;
        } finally {
            lock.readLock().unlock();
        }
    }

    @VisibleForTesting
    protected class ReconfigurableTracerBuilder implements TracerBuilder {
        final TracerBuilder delegate;
        final String instrumentationScopeName;
        String schemaUrl;
        String instrumentationScopeVersion;
        final ReadWriteLock lock;

        public ReconfigurableTracerBuilder(
                TracerBuilder delegate, String instrumentationScopeName, ReadWriteLock lock) {
            this.delegate = Objects.requireNonNull(delegate);
            this.instrumentationScopeName = Objects.requireNonNull(instrumentationScopeName);
            this.lock = lock;
        }

        @Override
        public TracerBuilder setSchemaUrl(String schemaUrl) {
            delegate.setSchemaUrl(schemaUrl);
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public TracerBuilder setInstrumentationVersion(String instrumentationScopeVersion) {
            delegate.setInstrumentationVersion(instrumentationScopeVersion);
            this.instrumentationScopeVersion = instrumentationScopeVersion;
            return this;
        }

        @Override
        public Tracer build() {
            lock.readLock().lock();
            try {
                InstrumentationScope instrumentationScope =
                        new InstrumentationScope(instrumentationScopeName, schemaUrl, instrumentationScopeVersion);
                return tracers.computeIfAbsent(
                        instrumentationScope, k -> new ReconfigurableExtendedTracer(delegate.build(), lock));
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableExtendedTracer implements ExtendedTracer {
        final ReadWriteLock lock;

        ExtendedTracer delegate;

        public ReconfigurableExtendedTracer(Tracer delegate, ReadWriteLock lock) {
            this.lock = Objects.requireNonNull(lock, "lock");
            this.delegate = Objects.requireNonNull(requiresExtendedTracer(delegate), "delegate");
        }

        private static ExtendedTracer requiresExtendedTracer(Tracer tracer) {
            if (!(tracer instanceof ExtendedTracer)) {
                // code copied from
                // https://github.com/open-telemetry/opentelemetry-java/blob/v1.49.0/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/SdkTracer.java#L21-L27
                boolean incubatorAvailable = false;
                try {
                    Class.forName("io.opentelemetry.api.incubator.trace.ExtendedDefaultTracerProvider");
                    incubatorAvailable = true;
                } catch (ClassNotFoundException e) {
                    // Not available
                }

                throw new IllegalStateException("Delegate '" + tracer + "' must be an instance of ExtendedTracer. "
                        + "API incubator module is not on the classpath: " + incubatorAvailable);
            }
            return (ExtendedTracer) tracer;
        }

        @Override
        public ExtendedSpanBuilder spanBuilder(@Nonnull String spanName) {
            lock.readLock().lock();
            try {
                return delegate.spanBuilder(spanName);
            } finally {
                lock.readLock().unlock();
            }
        }

        public void setDelegate(Tracer delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = requiresExtendedTracer(delegate);
            } finally {
                lock.writeLock().unlock();
            }
        }

        public ExtendedTracer getDelegate() {
            lock.readLock().lock();
            try {
                return delegate;
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public boolean isEnabled() {
            lock.readLock().lock();
            try {
                return delegate.isEnabled();
            } finally {
                lock.readLock().unlock();
            }
        }
    }
}
