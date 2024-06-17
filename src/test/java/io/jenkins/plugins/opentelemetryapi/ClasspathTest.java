package io.jenkins.plugins.opentelemetryapi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import org.junit.Test;

public class ClasspathTest {
    @Test
    public void testClasspath() throws ClassNotFoundException {
        assertThat(Class.forName("io.opentelemetry.api.OpenTelemetry"), notNullValue());
    }

    @Test
    public void testGetOpenTelemetryInstance() {
        io.opentelemetry.api.GlobalOpenTelemetry.get();
        io.opentelemetry.api.incubator.events.GlobalEventLoggerProvider.get();
    }
}