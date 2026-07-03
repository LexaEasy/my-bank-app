package ru.practicum.bank.common.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

import java.util.function.Function;
import java.util.function.Supplier;

public class ResilientClientExecutor {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    ResilientClientExecutor(CircuitBreaker circuitBreaker, Retry retry) {
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    public static ResilientClientExecutor opened(String name) {
        var factory = ResilientClientFactory.withDefaults();
        var executor = factory.create(name);
        executor.circuitBreaker.transitionToOpenState();
        return executor;
    }

    public <T> T execute(Supplier<T> action, Function<Throwable, T> fallback) {
        try {
            Supplier<T> decorated = Retry.decorateSupplier(retry, action);
            decorated = CircuitBreaker.decorateSupplier(circuitBreaker, decorated);
            return decorated.get();
        } catch (CallNotPermittedException exception) {
            return fallback.apply(exception);
        } catch (Throwable exception) {
            return fallback.apply(exception);
        }
    }
}
