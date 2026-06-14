package ru.practicum.bank.transfer.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpNotificationsClient implements NotificationsClient {

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;

    public HttpNotificationsClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.services.notifications.base-url}") String notificationsBaseUrl,
            ServiceTokenProvider serviceTokenProvider
    ) {
        this.serviceTokenProvider = serviceTokenProvider;
        this.restClient = restClientBuilder
                .baseUrl(notificationsBaseUrl)
                .build();
    }

    @Override
    public void notify(NotificationRequest request) {
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
