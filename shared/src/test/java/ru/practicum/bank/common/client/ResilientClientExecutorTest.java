package ru.practicum.bank.common.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ResilientClientExecutorTest {

    @Test
    void shouldOpenAfterFailureThresholdAndUseFallbackWithoutActionCall() {
        var factory = new ResilientClientFactory(3, Duration.ofSeconds(5), 1, Duration.ZERO);
        var executor = factory.create("accountsService");
        var calls = new AtomicInteger();

        for (int attempt = 0; attempt < 3; attempt++) {
            String result = executor.execute(
                    () -> {
                        calls.incrementAndGet();
                        throw new IllegalStateException("Service unavailable");
                    },
                    exception -> "fallback"
            );

            assertThat(result).isEqualTo("fallback");
        }

        String result = executor.execute(
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
