package ru.practicum.bank.cash.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.practicum.bank.common.client.SimpleCircuitBreaker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;

class HttpNotificationsClientTest {

    private final ServiceTokenProvider serviceTokenProvider = mock(ServiceTokenProvider.class);

    @Test
    void shouldSendServiceTokenToNotificationsService() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var client = new HttpNotificationsClient(
                restClientBuilder,
                "http://notifications-service",
                serviceTokenProvider
        );
        when(serviceTokenProvider.getAccessToken()).thenReturn("service-token");

        server.expect(once(), requestTo("http://notifications-service/api/notifications"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .andRespond(withAccepted().contentType(MediaType.APPLICATION_JSON).body("""
                        {
                          "status": "ACCEPTED"
                        }
                        """));

        client.notify(new NotificationRequest(
                "ivan",
                "CASH_DEPOSIT",
                "Счёт пополнен на 250.00 RUB",
                "operation-1"
        ));

        server.verify();
    }

    @Test
    void shouldIgnoreNotificationFailureWhenCircuitBreakerFallbackRuns() {
        var restClientBuilder = RestClient.builder();
        var client = new HttpNotificationsClient(
                restClientBuilder,
                "http://notifications-service",
                serviceTokenProvider,
                SimpleCircuitBreaker.opened("notificationsService")
        );

        client.notify(new NotificationRequest(
                "ivan",
                "CASH_DEPOSIT",
                "Счёт пополнен на 250.00 RUB",
                "operation-1"
        ));
    }
}
