package ru.practicum.bank.frontui.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.practicum.bank.frontui.dto.TransferForm;
import ru.practicum.bank.frontui.dto.TransferRequest;
import ru.practicum.bank.frontui.dto.TransferResponse;

@Component
public class GatewayClient {

    private final RestClient restClient;

    public GatewayClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.gateway.base-url}") String gatewayBaseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(gatewayBaseUrl)
                .build();
    }

    public TransferResponse transfer(String accessToken, TransferForm form) {
        try {
            return restClient.post()
                    .uri("/api/transfers")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .body(new TransferRequest(form.recipientLogin(), form.amount(), form.currency()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new GatewayClientException("Gateway request failed: " + response.getStatusCode());
                    })
                    .body(TransferResponse.class);
        } catch (RestClientException exception) {
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }
}
