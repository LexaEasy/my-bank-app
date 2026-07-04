package ru.practicum.bank.cash.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import ru.practicum.bank.common.client.ResilientClientFactory;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    RestClientCustomizer restClientCustomizer(
            @Value("${bank.http-client.connect-timeout:2s}") Duration connectTimeout,
            @Value("${bank.http-client.read-timeout:5s}") Duration readTimeout
    ) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return builder -> builder.requestFactory(requestFactory);
    }

    @Bean
    ResilientClientFactory resilientClientFactory(
            @Value("${bank.http-client.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${bank.http-client.circuit-breaker.open-duration:5s}") Duration openDuration,
            @Value("${bank.http-client.retry.max-attempts:2}") int maxAttempts,
            @Value("${bank.http-client.retry.backoff:100ms}") Duration backoff
    ) {
        return new ResilientClientFactory(failureThreshold, openDuration, maxAttempts, backoff);
    }
}
