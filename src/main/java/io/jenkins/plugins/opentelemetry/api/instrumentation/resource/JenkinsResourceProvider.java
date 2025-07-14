/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.api.instrumentation.resource;

import io.jenkins.plugins.opentelemetry.api.semconv.JenkinsAttributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JenkinsResourceProvider implements ResourceProvider {
    private static final Logger LOGGER = Logger.getLogger(JenkinsResourceProvider.class.getName());

    @Override
    public Resource createResource(ConfigProperties config) {
        ResourceBuilder resourceBuilder = Resource.builder();
        resourceBuilder.put(ServiceAttributes.SERVICE_NAME, JenkinsAttributes.JENKINS);
        resourceBuilder.put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, JenkinsAttributes.JENKINS);

        Optional<String> jenkinsVersion =
                Optional.ofNullable(config.getString(JenkinsAttributes.JENKINS_VERSION.getKey()));
        jenkinsVersion.ifPresent(version -> resourceBuilder.put(ServiceAttributes.SERVICE_VERSION, version));
        // Report jenkins.version even if service.version is overriden
        jenkinsVersion.ifPresent(version -> resourceBuilder.put(JenkinsAttributes.JENKINS_VERSION.getKey(), version));

        Optional<String> jenkinsUrl = Optional.ofNullable(config.getString(JenkinsAttributes.JENKINS_URL.getKey()));
        jenkinsUrl.ifPresent(version -> resourceBuilder.put(JenkinsAttributes.JENKINS_URL, version));

        Optional<String> serviceInstanceId =
                Optional.ofNullable(config.getString(ServiceIncubatingAttributes.SERVICE_INSTANCE_ID.getKey()));
        serviceInstanceId.ifPresent(
                version -> resourceBuilder.put(ServiceIncubatingAttributes.SERVICE_INSTANCE_ID, version));
        Resource resource = resourceBuilder.build();
        LOGGER.log(Level.FINER, () -> "Jenkins resource: " + resource);
        return resource;
    }
}
