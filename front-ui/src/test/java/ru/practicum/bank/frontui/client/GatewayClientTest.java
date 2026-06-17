package ru.practicum.bank.frontui.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.practicum.bank.frontui.dto.CashForm;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GatewayClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldShowGatewayErrorMessage() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new GatewayClient(restClientBuilder, "http://bank-gateway", objectMapper);

        server.expect(once(), requestTo("http://bank-gateway/api/cash/withdraw"))
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
                "http://bank-gateway",
                SimpleCircuitBreaker.opened("bankGateway"),
                objectMapper
        );

        assertThatThrownBy(() -> client.withdraw("user-token", new CashForm(new BigDecimal("100.00"), "RUB")))
                .isInstanceOf(GatewayClientException.class)
                .hasMessage("Банковские сервисы временно недоступны");
    }
}
