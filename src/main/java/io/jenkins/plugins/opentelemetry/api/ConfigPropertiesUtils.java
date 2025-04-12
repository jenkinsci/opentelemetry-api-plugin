/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.api;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility methods for working with {@link ConfigProperties}.
 */
class ConfigPropertiesUtils {

    /**
     * Helper because there is no implementation of the "i.o.s.a.s.ConfigProperties" interface.
     */
    static ConfigProperties emptyConfig(){
        return DefaultConfigProperties.createFromMap(Collections.emptyMap());
    }

    static String prettyPrintOtelSdkConfig(ConfigProperties configProperties, Resource resource) {
        return "SDK [" +
                "config: " + prettyPrintConfiguration(configProperties) + ", "+
                "resource: " + prettyPrintResource(resource) +
                "]";
    }
    static String prettyPrintConfiguration(ConfigProperties config) {
        Map<String, String> message = new LinkedHashMap<>();
        for (String attributeName : noteworthyConfigurationPropertyNames) {
            final String attributeValue = config.getString(attributeName);
            if (attributeValue != null) {
                message.put(attributeName, attributeValue);
            }
        }
        return message
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "))
                + "...";
    }
    static String prettyPrintResource(@Nullable Resource resource) {
        if (resource == null) {
            return "#null#";
        }
        Map<String, String> message = new LinkedHashMap<>();
        for (AttributeKey<?> attributeKey : noteworthyResourceAttributeKeys) {
            Object attributeValue = resource.getAttribute(attributeKey);
            if (attributeValue != null) {
                message.put(attributeKey.getKey(), Objects.toString(attributeValue, "#null#"));
            }
        }
        return message
                .entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "))
                + "...";
    }
    private final static List<String> noteworthyConfigurationPropertyNames = Arrays.asList(
            "otel.resource.attributes", "otel.service.name",
            "otel.traces.exporter", "otel.metrics.exporter", "otel.logs.exporter",
            "otel.exporter.otlp.endpoint"  , "otel.exporter.otlp.traces.endpoint", "otel.exporter.otlp.metrics.endpoint",
            "otel.exporter.jaeger.endpoint", "otel.exporter.prometheus.port");

    private final static List<AttributeKey<?>> noteworthyResourceAttributeKeys = Arrays.asList(
            ServiceAttributes.SERVICE_NAME, ServiceIncubatingAttributes.SERVICE_NAMESPACE, ServiceAttributes.SERVICE_VERSION
    ) ;

}
