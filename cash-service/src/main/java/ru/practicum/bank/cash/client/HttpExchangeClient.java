package ru.practicum.bank.cash.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.practicum.bank.common.client.ResilientClientExecutor;
import ru.practicum.bank.common.client.ResilientClientFactory;
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

@Component
public class HttpExchangeClient implements ExchangeClient {

    private static final Logger log = LoggerFactory.getLogger(HttpExchangeClient.class);

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;
    private final ResilientClientExecutor clientExecutor;

    @Autowired
    public HttpExchangeClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.services.exchange.base-url}") String exchangeBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            ResilientClientFactory resilientClientFactory
    ) {
        this(
                restClientBuilder,
                exchangeBaseUrl,
                serviceTokenProvider,
                resilientClientFactory.create("exchangeService")
        );
    }

    HttpExchangeClient(
            RestClient.Builder restClientBuilder,
            String exchangeBaseUrl,
            ServiceTokenProvider serviceTokenProvider
    ) {
        this(
                restClientBuilder,
                exchangeBaseUrl,
                serviceTokenProvider,
                ResilientClientFactory.withDefaults()
        );
    }

    HttpExchangeClient(
            RestClient.Builder restClientBuilder,
            String exchangeBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            ResilientClientExecutor clientExecutor
    ) {
        this.serviceTokenProvider = serviceTokenProvider;
        this.clientExecutor = clientExecutor;
        this.restClient = restClientBuilder
                .baseUrl(exchangeBaseUrl)
                .build();
    }

    @Override
    public ConversionResponse convert(Currency sourceCurrency, Currency targetCurrency, BigDecimal amount) {
        return clientExecutor.execute(
                () -> convertWithoutCircuitBreaker(sourceCurrency, targetCurrency, amount),
                this::exchangeFallback
        );
    }

    private ConversionResponse convertWithoutCircuitBreaker(
            Currency sourceCurrency,
            Currency targetCurrency,
            BigDecimal amount
    ) {
        try {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Exchange downstream request prepared operationType=EXCHANGE currency={} targetCurrency={} source=cash-service targetService=exchange-service",
                        sourceCurrency,
                        targetCurrency
                );
            }
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/exchange/conversion")
                            .queryParam("sourceCurrency", sourceCurrency)
                            .queryParam("targetCurrency", targetCurrency)
                            .queryParam("amount", amount)
                            .build())
                    .headers(headers -> headers.setBearerAuth(serviceTokenProvider.getAccessToken()))
                    .retrieve()
                    .body(ConversionResponse.class);
            if (response == null) {
                throw new ExchangeClientException("Exchange service returned empty response");
            }
            return response;
        } catch (RestClientException exception) {
            log.error(
                    "Exchange downstream request failed operationType=EXCHANGE currency={} targetCurrency={} status=error errorCategory=downstream_unavailable errorType={} source=cash-service targetService=exchange-service",
                    sourceCurrency,
                    targetCurrency,
                    exception.getClass().getSimpleName()
            );
            throw new ExchangeClientException("Exchange service request failed", exception);
        }
    }

    private ConversionResponse exchangeFallback(Throwable exception) {
        if (exception instanceof ExchangeClientException exchangeClientException) {
            throw exchangeClientException;
        }
        log.error(
                "Exchange downstream retries exhausted status=error errorCategory=downstream_unavailable errorType={} source=cash-service targetService=exchange-service",
                exception.getClass().getSimpleName()
        );
        throw new ExchangeClientException("Exchange service is temporarily unavailable", exception);
    }
}
