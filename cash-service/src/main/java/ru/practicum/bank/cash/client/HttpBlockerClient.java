package ru.practicum.bank.cash.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.practicum.bank.common.client.ResilientClientExecutor;
import ru.practicum.bank.common.client.ResilientClientFactory;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;

@Component
public class HttpBlockerClient implements BlockerClient {

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;
    private final ResilientClientExecutor clientExecutor;

    @Autowired
    public HttpBlockerClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.services.blocker.base-url}") String blockerBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            ResilientClientFactory resilientClientFactory
    ) {
        this(
                restClientBuilder,
                blockerBaseUrl,
                serviceTokenProvider,
                resilientClientFactory.create("blockerService")
        );
    }

    HttpBlockerClient(
            RestClient.Builder restClientBuilder,
            String blockerBaseUrl,
            ServiceTokenProvider serviceTokenProvider
    ) {
        this(
                restClientBuilder,
                blockerBaseUrl,
                serviceTokenProvider,
                ResilientClientFactory.withDefaults()
        );
    }

    HttpBlockerClient(
            RestClient.Builder restClientBuilder,
            String blockerBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            ResilientClientExecutor clientExecutor
    ) {
        this.serviceTokenProvider = serviceTokenProvider;
        this.clientExecutor = clientExecutor;
        this.restClient = restClientBuilder
                .baseUrl(blockerBaseUrl)
                .build();
    }

    @Override
    public OperationCheckResponse check(OperationCheckRequest request) {
        return clientExecutor.execute(
                () -> checkWithoutCircuitBreaker(request),
                this::blockerFallback
        );
    }

    private OperationCheckResponse checkWithoutCircuitBreaker(OperationCheckRequest request) {
        try {
            var response = restClient.post()
                    .uri("/api/blocker/check")
                    .headers(headers -> headers.setBearerAuth(serviceTokenProvider.getAccessToken()))
                    .body(request)
                    .retrieve()
                    .body(OperationCheckResponse.class);
            if (response == null) {
                throw new BlockerClientException("Blocker service returned empty response");
            }
            return response;
        } catch (RestClientException exception) {
            throw new BlockerClientException("Blocker service request failed", exception);
        }
    }

    private OperationCheckResponse blockerFallback(Throwable exception) {
        if (exception instanceof BlockerClientException blockerClientException) {
            throw blockerClientException;
        }
        throw new BlockerClientException("Сервис проверки операций временно недоступен", exception);
    }
}
