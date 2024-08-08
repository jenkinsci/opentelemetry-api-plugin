package io.jenkins.plugins.opentelemetry.api;

import com.google.inject.AbstractModule;
import hudson.Extension;
import io.opentelemetry.api.OpenTelemetry;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Support using @{@link javax.inject.Inject} to inject the OpenTelemetry instance in Jenkins @{@link Extension}.
 */
@Extension
public class OpenTelemetryApiGuiceModule extends AbstractModule  {
    static final Logger logger = Logger.getLogger(OpenTelemetryApiGuiceModule.class.getName());

    public OpenTelemetryApiGuiceModule() {
        logger.log(Level.FINE, "Creating OpenTelemetryApiGuiceModule");
    }

    @Override
    public void configure() {
        logger.log(Level.FINE, "Configuring OpenTelemetryApiGuiceModule");
        ReconfigurableOpenTelemetry reconfigurableOpenTelemetry = ReconfigurableOpenTelemetry.get();
        bind(OpenTelemetry.class).toInstance(reconfigurableOpenTelemetry);
        bind(ExtendedOpenTelemetry.class).toInstance(reconfigurableOpenTelemetry);
        bind(ReconfigurableOpenTelemetry.class).toInstance(reconfigurableOpenTelemetry);
    }
}
