package ru.practicum.bank.transfer.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.practicum.bank.common.client.SimpleCircuitBreaker;
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

@Component
public class HttpExchangeClient implements ExchangeClient {

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;
    private final SimpleCircuitBreaker circuitBreaker;

    @Autowired
    public HttpExchangeClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.services.exchange.base-url}") String exchangeBaseUrl,
            ServiceTokenProvider serviceTokenProvider
    ) {
        this(
                restClientBuilder,
                exchangeBaseUrl,
                serviceTokenProvider,
                SimpleCircuitBreaker.withDefaults("exchangeService")
        );
    }

    HttpExchangeClient(
            RestClient.Builder restClientBuilder,
            String exchangeBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            SimpleCircuitBreaker circuitBreaker
    ) {
        this.serviceTokenProvider = serviceTokenProvider;
        this.circuitBreaker = circuitBreaker;
        this.restClient = restClientBuilder
                .baseUrl(exchangeBaseUrl)
                .build();
    }

    @Override
    public ConversionResponse convert(Currency sourceCurrency, Currency targetCurrency, BigDecimal amount) {
        return circuitBreaker.execute(
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
            throw new ExchangeClientException("Exchange service request failed", exception);
        }
    }

    private ConversionResponse exchangeFallback(Throwable exception) {
        if (exception instanceof ExchangeClientException exchangeClientException) {
            throw exchangeClientException;
        }
        throw new ExchangeClientException("Сервис курсов валют временно недоступен", exception);
    }
}
