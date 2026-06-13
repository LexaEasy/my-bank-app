package ru.practicum.bank.cash.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpAccountsClient implements AccountsClient {

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
    public AccountsBalanceResponse deposit(AccountsBalanceOperationRequest request) {
        return post("/api/accounts/internal/balance/deposit", request);
    }

    @Override
    public AccountsBalanceResponse withdraw(AccountsBalanceOperationRequest request) {
        return post("/api/accounts/internal/balance/withdraw", request);
    }

    private AccountsBalanceResponse post(String uri, AccountsBalanceOperationRequest request) {
        try {
            return restClient.post()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(serviceTokenProvider.getAccessToken()))
                    .body(request)
                    .retrieve()
                    .body(AccountsBalanceResponse.class);
        } catch (RestClientException exception) {
            throw new AccountsClientException("Accounts service request failed", exception);
        }
    }
}
