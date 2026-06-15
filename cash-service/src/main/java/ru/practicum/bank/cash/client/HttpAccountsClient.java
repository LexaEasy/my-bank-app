package ru.practicum.bank.cash.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.practicum.bank.cash.dto.ApiErrorResponse;

@Component
public class HttpAccountsClient implements AccountsClient {

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
}
