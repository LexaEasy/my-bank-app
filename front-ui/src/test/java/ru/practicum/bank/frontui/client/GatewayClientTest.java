package ru.practicum.bank.frontui.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.practicum.bank.common.client.SimpleCircuitBreaker;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.frontui.dto.CashForm;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GatewayClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldLoadExchangeRatesFromGateway() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new GatewayClient(
                restClientBuilder,
                "http://accounts-service:8081",
                "http://cash-service:8082",
                "http://transfer-service:8083",
                "http://exchange-service:8086",
                SimpleCircuitBreaker.withDefaults("bankServices"),
                objectMapper
        );

        server.expect(once(), requestTo("http://exchange-service:8086/api/exchange/rates"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "currency": "RUB",
                            "buyRate": "1.0000",
                            "sellRate": "1.0000",
                            "updatedAt": null
                          },
                          {
                            "currency": "USD",
                            "buyRate": "90.0000",
                            "sellRate": "92.0000",
                            "updatedAt": null
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var rates = client.getExchangeRates("user-token");

        assertThat(rates).hasSize(2);
        assertThat(rates.get(1).currency()).isEqualTo(Currency.USD);
        assertThat(rates.get(1).buyRate()).isEqualByComparingTo("90.0000");
        server.verify();
    }

    @Test
    void shouldShowGatewayErrorMessage() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new GatewayClient(
                restClientBuilder,
                "http://accounts-service:8081",
                "http://cash-service:8082",
                "http://transfer-service:8083",
                "http://exchange-service:8086",
                SimpleCircuitBreaker.withDefaults("bankServices"),
                objectMapper
        );

        server.expect(once(), requestTo("http://cash-service:8082/api/cash/withdraw"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": "ACCOUNTS_SERVICE_UNAVAILABLE",
                                  "message": "Недостаточно средств"
                                }
                                """));

        assertThatThrownBy(() -> client.withdraw("user-token", new CashForm(new BigDecimal("999999.00"), "RUB")))
                .isInstanceOf(GatewayClientException.class)
                .hasMessage("Недостаточно средств");

        server.verify();
    }

    @Test
    void shouldReturnCircuitBreakerFallbackMessage() {
        var restClientBuilder = RestClient.builder();
        var client = new GatewayClient(
                restClientBuilder,
                "http://accounts-service:8081",
                "http://cash-service:8082",
                "http://transfer-service:8083",
                "http://exchange-service:8086",
                SimpleCircuitBreaker.opened("bankServices"),
                objectMapper
        );

        assertThatThrownBy(() -> client.withdraw("user-token", new CashForm(new BigDecimal("100.00"), "RUB")))
                .isInstanceOf(GatewayClientException.class)
                .hasMessage("Банковские сервисы временно недоступны");
    }
}
