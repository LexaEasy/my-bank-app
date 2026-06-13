package ru.practicum.bank.cash.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.practicum.bank.cash.model.Currency;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAccountsClientTest {

    private final ServiceTokenProvider serviceTokenProvider = mock(ServiceTokenProvider.class);

    @Test
    void shouldSendServiceTokenToAccountsService() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new HttpAccountsClient(restClientBuilder, "http://accounts-service", serviceTokenProvider);
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
}
