//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.jenkins.plugins.opentelemetry.api.logs;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.Body;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.resources.Resource;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.Immutable;

/**
 * <p>
 * Inspired by `io.opentelemetry.sdk.testing.logs.TestLogRecordData`.
 * </p>
 * <p>
 * {@link AutoValue_TestLogRecordData} is manually recopied with the source code. See its javadoc.
 * </p>
 */
@Immutable
@AutoValue
public abstract class TestLogRecordData implements LogRecordData {
    public static Builder builder() {
        return (new AutoValue_TestLogRecordData.Builder()).setResource(Resource.empty()).setInstrumentationScopeInfo(InstrumentationScopeInfo.empty()).setTimestamp(0L, TimeUnit.NANOSECONDS).setObservedTimestamp(0L, TimeUnit.NANOSECONDS).setSpanContext(SpanContext.getInvalid()).setSeverity(Severity.UNDEFINED_SEVERITY_NUMBER).setBody("").setAttributes(Attributes.empty()).setTotalAttributeCount(0);
    }

    TestLogRecordData() {
    }

    @com.google.auto.value.AutoValue.Builder
    public abstract static class Builder {
        public Builder() {
        }

        abstract TestLogRecordData autoBuild();

        public TestLogRecordData build() {
            return this.autoBuild();
        }

        public abstract Builder setResource(Resource var1);

        public abstract Builder setInstrumentationScopeInfo(InstrumentationScopeInfo var1);

        public Builder setTimestamp(Instant instant) {
            return this.setTimestampEpochNanos(TimeUnit.SECONDS.toNanos(instant.getEpochSecond()) + (long) instant.getNano());
        }

        public Builder setTimestamp(long timestamp, TimeUnit unit) {
            return this.setTimestampEpochNanos(unit.toNanos(timestamp));
        }

        abstract Builder setTimestampEpochNanos(long var1);

        public Builder setObservedTimestamp(Instant instant) {
            return this.setObservedTimestampEpochNanos(TimeUnit.SECONDS.toNanos(instant.getEpochSecond()) + (long) instant.getNano());
        }

        public Builder setObservedTimestamp(long timestamp, TimeUnit unit) {
            return this.setObservedTimestampEpochNanos(unit.toNanos(timestamp));
        }

        abstract Builder setObservedTimestampEpochNanos(long var1);

        public abstract Builder setSpanContext(SpanContext var1);

        public abstract Builder setSeverity(Severity var1);

        public abstract Builder setSeverityText(String var1);

        public Builder setBody(String body) {
            return this.setBody(Body.string(body));
        }

        abstract Builder setBody(Body var1);

        public abstract Builder setAttributes(Attributes var1);

        public abstract Builder setTotalAttributeCount(int var1);
    }
}
