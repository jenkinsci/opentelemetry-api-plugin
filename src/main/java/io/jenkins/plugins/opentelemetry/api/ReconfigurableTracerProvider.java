/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.api;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p>
 * A {@link TracerProvider} that allows to reconfigure the {@link Tracer}s.
 * </p>
 * <p>
 * We need reconfigurability because Jenkins supports changing the configuration of the OpenTelemetry params at runtime.
 * All instantiated tracers are reconfigured when the configuration changes, when
 * {@link ReconfigurableTracerProvider#setDelegate(TracerProvider)} is invoked.
 * </p>
 */
class ReconfigurableTracerProvider implements TracerProvider {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private TracerProvider delegate;

    private final ConcurrentMap<InstrumentationScope, ReconfigurableTracer> tracers = new ConcurrentHashMap<>();

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
                    instrumentationScope -> new ReconfigurableTracer(delegate.get(instrumentationScope.instrumentationScopeName), lock));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDelegate(TracerProvider delegate) {
        lock.writeLock().lock();
        try {
            this.delegate = delegate;
            tracers.forEach((instrumentationScope, reconfigurableTracer) -> {
                TracerBuilder tracerBuilder = delegate.tracerBuilder(instrumentationScope.instrumentationScopeName);
                Optional.ofNullable(instrumentationScope.instrumentationScopeVersion).ifPresent(tracerBuilder::setInstrumentationVersion);
                Optional.ofNullable(instrumentationScope.schemaUrl).ifPresent(tracerBuilder::setSchemaUrl);
                reconfigurableTracer.setDelegate(tracerBuilder.build());
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
                    instrumentationScope -> new ReconfigurableTracer(delegate.get(instrumentationScopeName, instrumentationScopeVersion), lock));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public TracerBuilder tracerBuilder(String instrumentationScopeName) {
        lock.readLock().lock();
        try {
            return new ReconfigurableTracerBuilder(delegate.tracerBuilder(instrumentationScopeName), instrumentationScopeName, lock);
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

        public ReconfigurableTracerBuilder(TracerBuilder delegate, String instrumentationScopeName, ReadWriteLock lock) {
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
        public ExtendedTracer build() {
            lock.readLock().lock();
            try {
                InstrumentationScope instrumentationScope = new InstrumentationScope(instrumentationScopeName, schemaUrl, instrumentationScopeVersion);
                return tracers.computeIfAbsent(instrumentationScope, k -> new ReconfigurableTracer(delegate.build(), lock));
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableTracer implements ExtendedTracer {
        final ReadWriteLock lock;

        Tracer delegate;

        public ReconfigurableTracer(Tracer delegate, ReadWriteLock lock) {
            this.lock = lock;
            this.delegate = delegate;
        }

        @Override
        public SpanBuilder spanBuilder(@Nonnull String spanName) {
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
                this.delegate = delegate;
            } finally {
                lock.writeLock().unlock();
            }
        }

        public Tracer getDelegate() {
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
                if (delegate instanceof ExtendedTracer) {
                    return ((ExtendedTracer) delegate).isEnabled();
                } else {
                    // It's the NO OP impl
                    return false;
                }
            } finally {
                lock.readLock().unlock();
            }
        }
    }

}
