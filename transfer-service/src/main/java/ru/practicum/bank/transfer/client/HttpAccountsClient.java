package ru.practicum.bank.transfer.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.practicum.bank.transfer.dto.ApiErrorResponse;
import ru.practicum.bank.transfer.service.TransferExecutor;
import ru.practicum.bank.transfer.service.TransferOperation;
import ru.practicum.bank.transfer.service.TransferResult;

@Component
public class HttpAccountsClient implements TransferExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;

    public HttpAccountsClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.services.accounts.base-url}") String accountsBaseUrl,
            ServiceTokenProvider serviceTokenProvider
    ) {
        this.serviceTokenProvider = serviceTokenProvider;
        this.restClient = restClientBuilder
                .baseUrl(accountsBaseUrl)
                .build();
    }

    @Override
    public TransferResult execute(TransferOperation operation) {
        try {
            var response = restClient.post()
                    .uri("/api/accounts/internal/balance/transfer")
                    .headers(headers -> headers.setBearerAuth(serviceTokenProvider.getAccessToken()))
                    .body(toAccountsRequest(operation))
                    .retrieve()
                    .body(AccountsTransferResponse.class);

            return new TransferResult(
                    response.senderLogin(),
                    response.recipientLogin(),
                    response.senderBalance(),
                    response.currency()
            );
        } catch (RestClientResponseException exception) {
            throw new AccountsClientException(extractMessage(exception), exception);
        } catch (RestClientException exception) {
            throw new AccountsClientException("Accounts service request failed", exception);
        }
    }

    private String extractMessage(RestClientResponseException exception) {
        try {
            var error = OBJECT_MAPPER.readValue(exception.getResponseBodyAsString(), ApiErrorResponse.class);
            if (error.message() != null && !error.message().isBlank()) {
                return error.message();
            }
        } catch (JsonProcessingException ignored) {
            // Use a stable fallback when downstream does not return the expected error body.
        }
        return "Accounts service request failed";
    }

    private AccountsTransferRequest toAccountsRequest(TransferOperation operation) {
        return new AccountsTransferRequest(
                operation.senderLogin(),
                operation.recipientLogin(),
                operation.amount(),
                operation.currency(),
                operation.operationId()
        );
    }
}
