package ru.practicum.bank.common.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;
import java.util.function.Predicate;

public class ResilientClientFactory {

    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final Duration DEFAULT_OPEN_DURATION = Duration.ofSeconds(5);
    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final Duration DEFAULT_BACKOFF = Duration.ofMillis(100);

    private final int failureThreshold;
    private final Duration openDuration;
    private final int maxAttempts;
    private final Duration backoff;

    public ResilientClientFactory(
            int failureThreshold,
            Duration openDuration,
            int maxAttempts,
            Duration backoff
    ) {
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    public static ResilientClientFactory withDefaults() {
        return new ResilientClientFactory(
                DEFAULT_FAILURE_THRESHOLD,
                DEFAULT_OPEN_DURATION,
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_BACKOFF
        );
    }

    public ResilientClientExecutor create(String name) {
        return create(name, exception -> true);
    }

    public ResilientClientExecutor create(String name, Predicate<Throwable> recoverable) {
        var circuitBreaker = CircuitBreaker.of(name, circuitBreakerConfig(recoverable));
        var retry = Retry.of(name, retryConfig(recoverable));
        return new ResilientClientExecutor(circuitBreaker, retry);
    }

    private CircuitBreakerConfig circuitBreakerConfig(Predicate<Throwable> recoverable) {
        return CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(failureThreshold)
                .minimumNumberOfCalls(failureThreshold)
                .failureRateThreshold(100.0f)
                .permittedNumberOfCallsInHalfOpenState(1)
                .waitDurationInOpenState(openDuration)
                .recordException(recoverable)
                .build();
    }

    private RetryConfig retryConfig(Predicate<Throwable> recoverable) {
        return RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(backoff)
                .retryOnException(recoverable)
                .build();
    }
}
