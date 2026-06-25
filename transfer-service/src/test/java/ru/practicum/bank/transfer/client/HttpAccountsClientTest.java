package ru.practicum.bank.transfer.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.practicum.bank.common.client.SimpleCircuitBreaker;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.transfer.service.TransferOperation;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAccountsClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ServiceTokenProvider serviceTokenProvider = mock(ServiceTokenProvider.class);

    @Test
    void shouldSendServiceTokenToAccountsService() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new HttpAccountsClient(
                restClientBuilder,
                "http://accounts-service",
                serviceTokenProvider,
                objectMapper
        );
        when(serviceTokenProvider.getAccessToken()).thenReturn("service-token");

        server.expect(once(), requestTo("http://accounts-service/api/accounts/internal/balance/transfer"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withSuccess("""
                        {
                          "senderLogin": "ivan",
                          "recipientLogin": "olga",
                          "senderBalance": "800.00",
                          "currency": "RUB"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.execute(new TransferOperation(
                "ivan",
                "olga",
                new BigDecimal("200.00"),
                Currency.RUB,
                "operation-1"
        ));

        assertThat(response.senderLogin()).isEqualTo("ivan");
        assertThat(response.recipientLogin()).isEqualTo("olga");
        assertThat(response.senderBalance()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(response.currency()).isEqualTo("RUB");
        server.verify();
    }

    @Test
    void shouldPreserveAccountsServiceErrorMessage() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new HttpAccountsClient(
                restClientBuilder,
                "http://accounts-service",
                serviceTokenProvider,
                objectMapper
        );
        when(serviceTokenProvider.getAccessToken()).thenReturn("service-token");

        server.expect(once(), requestTo("http://accounts-service/api/accounts/internal/balance/transfer"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": "INSUFFICIENT_FUNDS",
                                  "message": "Недостаточно средств"
                                }
                                """));

        assertThatThrownBy(() -> client.execute(new TransferOperation(
                "ivan",
                "olga",
                new BigDecimal("999999.00"),
                Currency.RUB,
                "operation-1"
        )))
                .isInstanceOf(AccountsClientException.class)
                .hasMessage("Недостаточно средств");

        server.verify();
    }

    @Test
    void shouldReturnCircuitBreakerFallbackMessage() {
        var restClientBuilder = RestClient.builder();
        var client = new HttpAccountsClient(
                restClientBuilder,
                "http://accounts-service",
                serviceTokenProvider,
                SimpleCircuitBreaker.opened("accountsService"),
                objectMapper
        );

        assertThatThrownBy(() -> client.execute(new TransferOperation(
                "ivan",
                "olga",
                new BigDecimal("200.00"),
                Currency.RUB,
                "operation-1"
        )))
                .isInstanceOf(AccountsClientException.class)
                .hasMessage("Сервис счетов временно недоступен");
    }
}
