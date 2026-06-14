package ru.practicum.bank.transfer.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.practicum.bank.transfer.service.TransferExecutor;
import ru.practicum.bank.transfer.service.TransferOperation;
import ru.practicum.bank.transfer.service.TransferResult;

@Component
public class HttpAccountsClient implements TransferExecutor {

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
        } catch (RestClientException exception) {
            throw new AccountsClientException("Accounts service request failed", exception);
        }
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
