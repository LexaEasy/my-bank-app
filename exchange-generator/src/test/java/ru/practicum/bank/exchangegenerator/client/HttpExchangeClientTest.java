package ru.practicum.bank.exchangegenerator.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.practicum.bank.common.client.SimpleCircuitBreaker;
import ru.practicum.bank.common.dto.exchange.ExchangeRateUpdateRequest;
import ru.practicum.bank.common.dto.exchange.ExchangeRatesUpdateRequest;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpExchangeClientTest {

    private final ServiceTokenProvider serviceTokenProvider = mock(ServiceTokenProvider.class);

    @Test
    void shouldSendServiceTokenToExchangeService() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new HttpExchangeClient(
                restClientBuilder,
                "http://exchange-service",
                serviceTokenProvider
        );
        when(serviceTokenProvider.getAccessToken()).thenReturn("service-token");

        server.expect(once(), requestTo("http://exchange-service/api/exchange/rates"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.updateRates(updateRequest());

        server.verify();
    }

    @Test
    void shouldIgnoreExchangeFailureWhenCircuitBreakerFallbackRuns() {
        var restClientBuilder = RestClient.builder();
        var client = new HttpExchangeClient(
                restClientBuilder,
                "http://exchange-service",
                serviceTokenProvider,
                SimpleCircuitBreaker.opened("exchangeService")
        );

        client.updateRates(updateRequest());
    }

    private ExchangeRatesUpdateRequest updateRequest() {
        return new ExchangeRatesUpdateRequest(List.of(
                new ExchangeRateUpdateRequest(Currency.USD, new BigDecimal("90.0000"), new BigDecimal("92.0000"))
        ));
    }
}
