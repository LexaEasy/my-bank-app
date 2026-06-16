package ru.practicum.bank.transfer.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Supplier;

class SimpleCircuitBreaker {

    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final Duration DEFAULT_OPEN_DURATION = Duration.ofSeconds(5);

    private final String name;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int failures;
    private Instant openedAt;

    private SimpleCircuitBreaker(String name, int failureThreshold, Duration openDuration, Clock clock) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    static SimpleCircuitBreaker withDefaults(String name) {
        return new SimpleCircuitBreaker(name, DEFAULT_FAILURE_THRESHOLD, DEFAULT_OPEN_DURATION, Clock.systemUTC());
    }

    static SimpleCircuitBreaker opened(String name) {
        var circuitBreaker = withDefaults(name);
        circuitBreaker.open();
        return circuitBreaker;
    }

    synchronized <T> T execute(Supplier<T> action, Function<Throwable, T> fallback) {
        if (state == State.OPEN && !isReadyForRetry()) {
            return fallback.apply(new IllegalStateException("Circuit breaker is open: " + name));
        }
        if (state == State.OPEN) {
            state = State.HALF_OPEN;
        }

        try {
            T result = action.get();
            close();
            return result;
        } catch (Throwable exception) {
            recordFailure();
            return fallback.apply(exception);
        }
    }

    private boolean isReadyForRetry() {
        return openedAt != null && !clock.instant().isBefore(openedAt.plus(openDuration));
    }

    private void recordFailure() {
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        failures++;
        if (failures >= failureThreshold) {
            open();
        }
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.instant();
    }

    private void close() {
        state = State.CLOSED;
        failures = 0;
        openedAt = null;
    }

    enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
