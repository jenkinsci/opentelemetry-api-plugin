package io.jenkins.plugins.opentelemetry;

import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.opentelemetry.instrumentation.apachehttpclient.v4_3.ApacheHttpClientTelemetry;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;

public class ApacheHttpClientInstrumentationTest {

    @Test
    public void test_instantiate_instrumented_http_client() {
        ReconfigurableOpenTelemetry openTelemetry = new ReconfigurableOpenTelemetry();
        HttpClientBuilder httpClientBuilder = ApacheHttpClientTelemetry.create(openTelemetry).newHttpClientBuilder();
        CloseableHttpClient httpClient = httpClientBuilder.build();
        System.out.println(httpClient);
    }
}
