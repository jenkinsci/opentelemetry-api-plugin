/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.api.semconv;

import io.opentelemetry.api.common.AttributeKey;
import jenkins.model.Jenkins;

/**
 * @see io.opentelemetry.api.common.Attributes
 * @see io.opentelemetry.semconv.ServiceAttributes
 */
public final class JenkinsAttributes {

    /**
     * @see Jenkins#getRootUrl()
     */
    public static final AttributeKey<String> JENKINS_URL = AttributeKey.stringKey("jenkins.url");

    public static final String JENKINS = "jenkins";

    public static final AttributeKey<String> JENKINS_VERSION = AttributeKey.stringKey("jenkins.version");

}
