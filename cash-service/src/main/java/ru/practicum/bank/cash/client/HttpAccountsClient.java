package ru.practicum.bank.cash.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.practicum.bank.cash.dto.ApiErrorResponse;
import ru.practicum.bank.common.client.SimpleCircuitBreaker;

@Component
public class HttpAccountsClient implements AccountsClient {

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;
    private final SimpleCircuitBreaker circuitBreaker;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpAccountsClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.services.accounts.base-url}") String accountsBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            ObjectMapper objectMapper
    ) {
        this(
                restClientBuilder,
                accountsBaseUrl,
                serviceTokenProvider,
                SimpleCircuitBreaker.withDefaults("accountsService"),
                objectMapper
        );
    }

    HttpAccountsClient(
            RestClient.Builder restClientBuilder,
            String accountsBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            SimpleCircuitBreaker circuitBreaker,
            ObjectMapper objectMapper
    ) {
        this.serviceTokenProvider = serviceTokenProvider;
        this.circuitBreaker = circuitBreaker;
        this.objectMapper = objectMapper;
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
        return circuitBreaker.execute(
                () -> postWithoutCircuitBreaker(uri, request),
                this::accountsFallback,
                this::shouldRecordFailure
        );
    }

    private AccountsBalanceResponse postWithoutCircuitBreaker(String uri, AccountsBalanceOperationRequest request) {
        try {
            return restClient.post()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(serviceTokenProvider.getAccessToken()))
                    .body(request)
                    .retrieve()
                    .body(AccountsBalanceResponse.class);
        } catch (RestClientResponseException exception) {
            var error = extractError(exception);
            throw new AccountsClientException(
                    error.message(),
                    exception.getStatusCode(),
                    error.code(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new AccountsClientException("Accounts service request failed", exception);
        }
    }

    private boolean shouldRecordFailure(Throwable exception) {
        if (exception instanceof AccountsClientException accountsClientException) {
            return accountsClientException.getStatusCode().is5xxServerError();
        }
        return true;
    }

    private AccountsBalanceResponse accountsFallback(Throwable exception) {
        if (exception instanceof AccountsClientException accountsClientException) {
            throw accountsClientException;
        }
        throw new AccountsClientException("Сервис счетов временно недоступен", exception);
    }

    private ApiErrorResponse extractError(RestClientResponseException exception) {
        try {
            var error = objectMapper.readValue(exception.getResponseBodyAsString(), ApiErrorResponse.class);
            if (isNotBlank(error.code()) && isNotBlank(error.message())) {
                return error;
            }
        } catch (JsonProcessingException ignored) {
            // Use a stable fallback when downstream does not return the expected error body.
        }
        return new ApiErrorResponse("ACCOUNTS_SERVICE_ERROR", "Accounts service request failed");
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
