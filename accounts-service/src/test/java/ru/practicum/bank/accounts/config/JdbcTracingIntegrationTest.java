package ru.practicum.bank.accounts.config;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "management.tracing.sampling.probability=1.0",
        "management.zipkin.tracing.export.enabled=false"
})
@AutoConfigureObservability
@Import(JdbcTracingIntegrationTest.TracingTestConfiguration.class)
class JdbcTracingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Tracer tracer;

    @Autowired
    private CapturingSpanHandler spanHandler;

    @BeforeEach
    void clearSpans() {
        spanHandler.clear();
    }

    @Test
    void shouldCreateSafeJdbcChildSpan() {
        var parent = tracer.nextSpan().name("http-parent").start();
        String traceId = parent.context().traceId();

        try (var ignored = tracer.withSpan(parent)) {
            assertThat(jdbcTemplate.queryForObject("select ?", Integer.class, 42)).isEqualTo(42);
        } finally {
            parent.end();
        }

        assertThat(spanHandler.spans())
                .anySatisfy(span -> {
                    assertThat(span.traceId()).isEqualTo(traceId);
                    assertThat(span.name()).containsIgnoringCase("query");
                    assertThat(span.details()).doesNotContain("42");
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TracingTestConfiguration {

        @Bean
        CapturingSpanHandler capturingSpanHandler() {
            return new CapturingSpanHandler();
        }
    }

    static final class CapturingSpanHandler extends SpanHandler {

        private final List<FinishedSpan> spans = new CopyOnWriteArrayList<>();

        @Override
        public boolean end(TraceContext context, MutableSpan span, Cause cause) {
            spans.add(new FinishedSpan(context.traceIdString(), span.name(), span.toString()));
            return true;
        }

        List<FinishedSpan> spans() {
            return List.copyOf(spans);
        }

        void clear() {
            spans.clear();
        }
    }

    record FinishedSpan(String traceId, String name, String details) {
    }
}
