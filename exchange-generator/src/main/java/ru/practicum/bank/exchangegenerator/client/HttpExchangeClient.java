package ru.practicum.bank.exchangegenerator.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.practicum.bank.common.client.ResilientClientExecutor;
import ru.practicum.bank.common.client.ResilientClientFactory;
import ru.practicum.bank.common.dto.exchange.ExchangeRatesUpdateRequest;

@Component
public class HttpExchangeClient implements ExchangeClient {

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
    public void updateRates(ExchangeRatesUpdateRequest request) {
        clientExecutor.execute(
                () -> {
                    updateRatesWithoutCircuitBreaker(request);
                    return null;
                },
                exception -> null
        );
    }

    private void updateRatesWithoutCircuitBreaker(ExchangeRatesUpdateRequest request) {
        try {
            restClient.put()
                    .uri("/api/exchange/rates")
                    .headers(headers -> headers.setBearerAuth(serviceTokenProvider.getAccessToken()))
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new ExchangeClientException("Exchange service request failed", exception);
        }
    }
}
