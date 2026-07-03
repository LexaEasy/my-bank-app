package ru.practicum.bank.cash.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.practicum.bank.common.client.SimpleCircuitBreaker;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
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

        server.expect(once(), requestTo("http://accounts-service/api/accounts/internal/balance/deposit"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withSuccess("""
                        {
                          "login": "ivan",
                          "balance": "1250.00",
                          "currency": "RUB"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.deposit(new AccountsBalanceOperationRequest(
                "ivan",
                new BigDecimal("250.00"),
                Currency.RUB,
                "operation-1"
        ));

        assertThat(response.login()).isEqualTo("ivan");
        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("1250.00"));
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

        server.expect(once(), requestTo("http://accounts-service/api/accounts/internal/balance/withdraw"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "code": "INSUFFICIENT_FUNDS",
                                  "message": "Недостаточно средств"
                                }
                                """));

        Throwable throwable = catchThrowable(() -> client.withdraw(new AccountsBalanceOperationRequest(
                "ivan",
                new BigDecimal("999999.00"),
                Currency.RUB,
                "operation-1"
        )));

        assertThat(throwable)
                .isInstanceOf(AccountsClientException.class)
                .hasMessage("Недостаточно средств");
        assertThat((AccountsClientException) throwable)
                .extracting(AccountsClientException::getStatusCode, AccountsClientException::getCode)
                .containsExactly(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS");

        server.verify();
    }

    @Test
    void shouldNotOpenCircuitBreakerAfterBusinessErrors() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new HttpAccountsClient(
                restClientBuilder,
                "http://accounts-service",
                serviceTokenProvider,
                objectMapper
        );
        when(serviceTokenProvider.getAccessToken()).thenReturn("service-token");

        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(once(), requestTo("http://accounts-service/api/accounts/internal/balance/withdraw"))
                    .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("""
                                    {
                                      "code": "INSUFFICIENT_FUNDS",
                                      "message": "Недостаточно средств"
                                    }
                                    """));
        }
        server.expect(once(), requestTo("http://accounts-service/api/accounts/internal/balance/withdraw"))
                .andRespond(withSuccess("""
                        {
                          "login": "ivan",
                          "balance": "500.00",
                          "currency": "RUB"
                        }
                        """, MediaType.APPLICATION_JSON));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> client.withdraw(balanceRequest("999999.00")))
                    .isInstanceOf(AccountsClientException.class)
                    .hasMessage("Недостаточно средств");
        }

        var response = client.withdraw(balanceRequest("100.00"));

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("500.00"));
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

        assertThatThrownBy(() -> client.deposit(new AccountsBalanceOperationRequest(
                "ivan",
                new BigDecimal("250.00"),
                Currency.RUB,
                "operation-1"
        )))
                .isInstanceOf(AccountsClientException.class)
                .hasMessage("Сервис счетов временно недоступен");
    }

    private AccountsBalanceOperationRequest balanceRequest(String amount) {
        return new AccountsBalanceOperationRequest(
                "ivan",
                new BigDecimal(amount),
                Currency.RUB,
                "operation-1"
        );
    }
}
