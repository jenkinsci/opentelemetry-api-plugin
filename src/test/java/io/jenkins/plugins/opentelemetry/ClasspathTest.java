package io.jenkins.plugins.opentelemetry;

import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.*;

class ClasspathTest {

    @Test
    void testClasspath() throws ClassNotFoundException {
        assertThat(Class.forName("io.opentelemetry.api.OpenTelemetry"), notNullValue());
    }

    @Test
    void testGetOpenTelemetryInstance() {
        io.opentelemetry.api.GlobalOpenTelemetry.get();
        io.opentelemetry.api.incubator.events.GlobalEventLoggerProvider.get();
    }
}