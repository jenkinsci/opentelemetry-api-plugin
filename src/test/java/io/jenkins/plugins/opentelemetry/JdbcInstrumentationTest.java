package io.jenkins.plugins.opentelemetry;

import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.Test;

import javax.sql.DataSource;

public class JdbcInstrumentationTest {

    @Test
    public void test_instantiate_instrumented_data_source() {
        try (ReconfigurableOpenTelemetry openTelemetry = new ReconfigurableOpenTelemetry()) {
            DataSource dataSource = JdbcTelemetry.create(openTelemetry).wrap(new BasicDataSource());
            System.out.println(dataSource);
        }
    }
}
