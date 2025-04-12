/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.api;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder;
import io.opentelemetry.api.incubator.logs.ExtendedLogger;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerBuilder;
import io.opentelemetry.api.logs.LoggerProvider;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p>
 * A {@link LoggerProvider} that allows to reconfigure the {@link Logger}s.
 * </p>
 * <p>
 * We need reconfigurability because Jenkins supports changing the configuration of the OpenTelemetry params at runtime.
 * All instantiated loggers are reconfigured when the configuration changes, when
 * {@link ReconfigurableLoggerProvider#setDelegate(LoggerProvider)} is invoked.
 * </p>
 *  * <p>
 *  *     IMPORTANT: requires the OpenTelemetry API incubator module to be on the classpath for provided
 *  *     {@link LoggerProvider} to create {@link ExtendedLogger}s.
 *  * </p>
 */
class ReconfigurableLoggerProvider implements LoggerProvider {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private LoggerProvider delegate;

    private final ConcurrentMap<InstrumentationScope, ReconfigurableExtendedLogger> loggers = new ConcurrentHashMap<>();

    public ReconfigurableLoggerProvider() {
        this(LoggerProvider.noop());
    }

    public ReconfigurableLoggerProvider(LoggerProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public LoggerBuilder loggerBuilder(String instrumentationScopeName) {
        lock.readLock().lock();
        try {
            return new ReconfigurableLoggerBuilder(delegate.loggerBuilder(instrumentationScopeName), instrumentationScopeName, lock);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Logger get(String instrumentationScopeName) {
        lock.readLock().lock();
        try {
            InstrumentationScope instrumentationScope = new InstrumentationScope(instrumentationScopeName);
            return loggers.computeIfAbsent(instrumentationScope, scope -> new ReconfigurableExtendedLogger(delegate.get(instrumentationScopeName), lock));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void setDelegate(LoggerProvider delegate) {
        lock.writeLock().lock();
        try {
            this.delegate = delegate;
            loggers.forEach((instrumentationScope, reconfigurableTracer) -> {
                LoggerBuilder loggerBuilder = delegate.loggerBuilder(instrumentationScope.instrumentationScopeName);
                Optional.ofNullable(instrumentationScope.instrumentationScopeVersion).ifPresent(loggerBuilder::setInstrumentationVersion);
                Optional.ofNullable(instrumentationScope.schemaUrl).ifPresent(loggerBuilder::setSchemaUrl);
                reconfigurableTracer.setDelegate(loggerBuilder.build());
            });
        } finally {
            lock.writeLock().unlock();
        }
    }

    @VisibleForTesting
    protected class ReconfigurableLoggerBuilder implements LoggerBuilder {
        final LoggerBuilder delegate;
        final String instrumentationScopeName;
        String schemaUrl;
        String instrumentationScopeVersion;
        final ReadWriteLock lock;

        public ReconfigurableLoggerBuilder(LoggerBuilder delegate, String instrumentationScopeName, ReadWriteLock lock) {
            this.delegate = Objects.requireNonNull(delegate);
            this.instrumentationScopeName = Objects.requireNonNull(instrumentationScopeName);
            this.lock = lock;
        }

        @Override
        public LoggerBuilder setSchemaUrl(String schemaUrl) {
            this.schemaUrl = schemaUrl;
            delegate.setSchemaUrl(schemaUrl);
            return this;
        }

        @Override
        public LoggerBuilder setInstrumentationVersion(String instrumentationScopeVersion) {
            this.instrumentationScopeVersion = instrumentationScopeVersion;
            delegate.setInstrumentationVersion(instrumentationScopeVersion);
            return this;
        }

        @Override
        public Logger build() {
            InstrumentationScope instrumentationScope = new InstrumentationScope(instrumentationScopeName, schemaUrl, instrumentationScopeVersion);
            return loggers.computeIfAbsent(instrumentationScope, scope -> new ReconfigurableExtendedLogger(delegate.build(), lock));
        }
    }

    @VisibleForTesting
    protected static class ReconfigurableExtendedLogger implements ExtendedLogger {
        ReadWriteLock lock;
        ExtendedLogger delegate;

        public ReconfigurableExtendedLogger(Logger delegate, ReadWriteLock lock) {
            this.delegate = requiresExtendedLogger(delegate);
            this.lock = lock;
        }

        @Override
        public ExtendedLogRecordBuilder logRecordBuilder() {
            lock.readLock().lock();
            try {
                return delegate.logRecordBuilder();
            } finally {
                lock.readLock().unlock();
            }
        }

        public void setDelegate(Logger delegate) {
            lock.writeLock().lock();
            try {
                this.delegate = requiresExtendedLogger(delegate);
            } finally {
                lock.writeLock().unlock();
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

        private static ExtendedLogger requiresExtendedLogger(Logger logger) {
            if (!(logger instanceof ExtendedLogger)) {
                // code copied from
                // https://github.com/open-telemetry/opentelemetry-java/blob/v1.49.0/sdk/logs/src/main/java/io/opentelemetry/sdk/logs/SdkLogger.java#L21-L27
                boolean incubatorAvailable = false;
                try {
                    Class.forName("io.opentelemetry.api.incubator.logs.ExtendedDefaultLoggerProvider");
                    incubatorAvailable = true;
                } catch (ClassNotFoundException e) {
                    // Not available
                }

                throw new IllegalStateException("Delegate '" + logger + "' must be an instance of Extended. " +
                        "API incubator module is not on the classpath: " + incubatorAvailable);
            }
            return (ExtendedLogger) logger;
        }
    }
}
