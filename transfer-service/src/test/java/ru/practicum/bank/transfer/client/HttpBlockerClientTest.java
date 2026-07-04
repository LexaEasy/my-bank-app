package ru.practicum.bank.transfer.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.practicum.bank.common.client.ResilientClientExecutor;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.model.OperationType;

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

class HttpBlockerClientTest {

    private final ServiceTokenProvider serviceTokenProvider = mock(ServiceTokenProvider.class);

    @Test
    void shouldSendServiceTokenToBlockerService() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new HttpBlockerClient(restClientBuilder, "http://blocker-service", serviceTokenProvider);
        when(serviceTokenProvider.getAccessToken()).thenReturn("service-token");

        server.expect(once(), requestTo("http://blocker-service/api/blocker/check"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withSuccess("""
                        {
                          "allowed": true
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.check(request());

        assertThat(response.allowed()).isTrue();
        assertThat(response.reason()).isNull();
        server.verify();
    }

    @Test
    void shouldThrowWhenBlockerServiceFails() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new HttpBlockerClient(restClientBuilder, "http://blocker-service", serviceTokenProvider);
        when(serviceTokenProvider.getAccessToken()).thenReturn("service-token");

        server.expect(times(2), requestTo("http://blocker-service/api/blocker/check"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.check(request()))
                .isInstanceOf(BlockerClientException.class)
                .hasMessage("Blocker service request failed");
        server.verify();
    }

    @Test
    void shouldReturnCircuitBreakerFallbackMessage() {
        var restClientBuilder = RestClient.builder();
        var client = new HttpBlockerClient(
                restClientBuilder,
                "http://blocker-service",
                serviceTokenProvider,
                ResilientClientExecutor.opened("blockerService")
        );

        assertThatThrownBy(() -> client.check(request()))
                .isInstanceOf(BlockerClientException.class)
                .hasMessage("Сервис проверки операций временно недоступен");
    }

    private OperationCheckRequest request() {
        return new OperationCheckRequest(
                "operation-1",
                OperationType.TRANSFER,
                null,
                "ivan",
                "olga",
                new BigDecimal("200.00"),
                Currency.RUB,
                new BigDecimal("200.00"),
                Currency.RUB
        );
    }
}
