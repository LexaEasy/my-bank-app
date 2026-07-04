package ru.practicum.bank.transfer.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.practicum.bank.common.client.ResilientClientExecutor;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpExchangeClientTest {

    private final ServiceTokenProvider serviceTokenProvider = mock(ServiceTokenProvider.class);

    @Test
    void shouldSendServiceTokenToExchangeService() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new HttpExchangeClient(restClientBuilder, "http://exchange-service", serviceTokenProvider);
        when(serviceTokenProvider.getAccessToken()).thenReturn("service-token");

        server.expect(once(), requestTo(
                        "http://exchange-service/api/exchange/conversion?sourceCurrency=USD&targetCurrency=CNY&amount=100.00"
                ))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withSuccess("""
                        {
                          "sourceCurrency": "USD",
                          "targetCurrency": "CNY",
                          "sourceAmount": "100.00",
                          "targetAmount": "741.94",
                          "rate": "7.419355",
                          "updatedAt": "2026-06-25T10:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.convert(Currency.USD, Currency.CNY, new BigDecimal("100.00"));

        assertThat(response.sourceCurrency()).isEqualTo(Currency.USD);
        assertThat(response.targetCurrency()).isEqualTo(Currency.CNY);
        assertThat(response.targetAmount()).isEqualByComparingTo("741.94");
        server.verify();
    }

    @Test
    void shouldThrowWhenExchangeServiceFails() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new HttpExchangeClient(restClientBuilder, "http://exchange-service", serviceTokenProvider);
        when(serviceTokenProvider.getAccessToken()).thenReturn("service-token");

        server.expect(times(2), requestTo(
                        "http://exchange-service/api/exchange/conversion?sourceCurrency=USD&targetCurrency=CNY&amount=100.00"
                ))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.convert(Currency.USD, Currency.CNY, new BigDecimal("100.00")))
                .isInstanceOf(ExchangeClientException.class)
                .hasMessage("Exchange service request failed");
        server.verify();
    }

    @Test
    void shouldReturnCircuitBreakerFallbackMessage() {
        var restClientBuilder = RestClient.builder();
        var client = new HttpExchangeClient(
                restClientBuilder,
                "http://exchange-service",
                serviceTokenProvider,
                ResilientClientExecutor.opened("exchangeService")
        );

        assertThatThrownBy(() -> client.convert(Currency.USD, Currency.CNY, new BigDecimal("100.00")))
                .isInstanceOf(ExchangeClientException.class)
                .hasMessage("Сервис курсов валют временно недоступен");
    }
}
