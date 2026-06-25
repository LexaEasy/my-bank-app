package ru.practicum.bank.common.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleCircuitBreakerTest {

    @Test
    void shouldOpenAfterFailureThresholdAndUseFallbackWithoutActionCall() {
        var circuitBreaker = SimpleCircuitBreaker.withDefaults("accountsService");
        var calls = new AtomicInteger();

        for (int attempt = 0; attempt < 3; attempt++) {
            String result = circuitBreaker.execute(
                    () -> {
                        calls.incrementAndGet();
                        throw new IllegalStateException("Service unavailable");
                    },
                    exception -> "fallback"
            );

            assertThat(result).isEqualTo("fallback");
        }

        String result = circuitBreaker.execute(
                () -> {
                    calls.incrementAndGet();
                    return "real response";
                },
                exception -> "fallback"
        );

        assertThat(result).isEqualTo("fallback");
        assertThat(calls).hasValue(3);
    }
}
