package ru.practicum.bank.transfer.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.practicum.bank.common.client.SimpleCircuitBreaker;

@Component
public class HttpNotificationsClient implements NotificationsClient {

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;
    private final SimpleCircuitBreaker circuitBreaker;

    @Autowired
    public HttpNotificationsClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.services.notifications.base-url}") String notificationsBaseUrl,
            ServiceTokenProvider serviceTokenProvider
    ) {
        this(
                restClientBuilder,
                notificationsBaseUrl,
                serviceTokenProvider,
                SimpleCircuitBreaker.withDefaults("notificationsService")
        );
    }

    HttpNotificationsClient(
            RestClient.Builder restClientBuilder,
            String notificationsBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            SimpleCircuitBreaker circuitBreaker
    ) {
        this.serviceTokenProvider = serviceTokenProvider;
        this.circuitBreaker = circuitBreaker;
        this.restClient = restClientBuilder
                .baseUrl(notificationsBaseUrl)
                .build();
    }

    @Override
    public void notify(NotificationRequest request) {
        circuitBreaker.execute(
                () -> {
                    notifyWithoutCircuitBreaker(request);
                    return null;
                },
                exception -> null
        );
    }

    private void notifyWithoutCircuitBreaker(NotificationRequest request) {
        try {
            restClient.post()
                    .uri("/api/notifications")
                    .headers(headers -> headers.setBearerAuth(serviceTokenProvider.getAccessToken()))
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new NotificationsClientException("Notifications service request failed", exception);
        }
    }
}
