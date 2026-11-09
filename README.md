[![Build Status](https://ci.jenkins.io/job/Plugins/job/opentelemetry-api-plugin/job/main/badge/icon)](https://ci.jenkins.io/job/Plugins/job/opentelemetry-api-plugin/job/main/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/opentelemetry-api-plugin.svg)](https://github.com/jenkinsci/opentelemetry-api-plugin/graphs/contributors)
[![Jenkins API Plugin](https://img.shields.io/jenkins/plugin/v/opentelemetry.svg)](https://plugins.jenkins.io/opentelemetry-api)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/opentelemetry-api-plugin.svg?label=changelog)](https://github.com/jenkinsci/opentelemetry-api-plugin/releases/latest)
[![Jenkins API Plugin Installs](https://img.shields.io/jenkins/plugin/i/opentelemetry-api.svg?color=blue)](https://plugins.jenkins.io/opentelemetry-api)
[![Otel SDK](https://img.shields.io/badge/otel--sdk-1.44.1-blue?style=flat&logo=opentelemetry)](https://github.com/open-telemetry/opentelemetry-java/releases/tag/v1.44.1)

# OpenTelemetry API Plugin

This plugin is a shared library. It provides the OpenTelemetry API jar so that other plugins can consume OpenTelemetry 
without duplication and without classpath collisions.

## Use with the OpenTelemetry Java agent

By default, the plugin builds its own OpenTelemetry SDK. When the Jenkins controller runs with the
[OpenTelemetry Java agent](https://opentelemetry.io/docs/zero-code/java/agent/), the agent has a second SDK, and the
agent's spans (for example HTTP client calls) can't be children of the Jenkins spans.

To use the agent's SDK instead, set `otel.instrumentation.jenkins.use-global-opentelemetry=true` on the controller, where
the agent is attached: as a system property (`-Dotel.instrumentation.jenkins.use-global-opentelemetry=true`) or as an
environment variable (`OTEL_INSTRUMENTATION_JENKINS_USE_GLOBAL_OPENTELEMETRY=true`). The configuration properties of the
OpenTelemetry plugin work too, but the plugin also sends them to the build agents, which then log a warning unless they
also run the agent. Then:

* The Jenkins spans, metrics and logs go through the agent's SDK. The agent's settings define the exporters, the
  endpoint, the resource (for example `service.name`) and the sampler. The agent reads them when the JVM starts.
* The agent's `opentelemetry-api` instrumentation must be enabled. It is enabled by default. With
  `otel.instrumentation.common.default-enabled=false`, also set `otel.instrumentation.opentelemetry-api.enabled=true`.
  Without it, the plugin logs a warning and builds its own SDK.

## Contributing

Refer to our [contribution guidelines](https://github.com/jenkinsci/.github/blob/master/CONTRIBUTING.md)

## LICENSE

Licensed under Apache Software License 2, see [LICENSE](LICENSE)
