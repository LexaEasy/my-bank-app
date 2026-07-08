package ru.practicum.bank.cash.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.practicum.bank.cash.dto.ApiErrorResponse;
import ru.practicum.bank.common.client.ResilientClientExecutor;
import ru.practicum.bank.common.client.ResilientClientFactory;

@Component
public class HttpAccountsClient implements AccountsClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAccountsClient.class);

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;
    private final ResilientClientExecutor clientExecutor;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpAccountsClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.services.accounts.base-url}") String accountsBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            ResilientClientFactory resilientClientFactory,
            ObjectMapper objectMapper
    ) {
        this(
                restClientBuilder,
                accountsBaseUrl,
                serviceTokenProvider,
                resilientClientFactory.create("accountsService", HttpAccountsClient::isRecoverable),
                objectMapper
        );
    }

    HttpAccountsClient(
            RestClient.Builder restClientBuilder,
            String accountsBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            ObjectMapper objectMapper
    ) {
        this(
                restClientBuilder,
                accountsBaseUrl,
                serviceTokenProvider,
                ResilientClientFactory.withDefaults(),
                objectMapper
        );
    }

    HttpAccountsClient(
            RestClient.Builder restClientBuilder,
            String accountsBaseUrl,
            ServiceTokenProvider serviceTokenProvider,
            ResilientClientExecutor clientExecutor,
            ObjectMapper objectMapper
    ) {
        this.serviceTokenProvider = serviceTokenProvider;
        this.clientExecutor = clientExecutor;
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
        return clientExecutor.execute(
                () -> postWithoutCircuitBreaker(uri, request),
                this::accountsFallback
        );
    }

    private AccountsBalanceResponse postWithoutCircuitBreaker(String uri, AccountsBalanceOperationRequest request) {
        try {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Accounts downstream request prepared operationId={} operationType={} currency={} source=cash-service targetService=accounts-service",
                        request.operationId(),
                        operationType(uri),
                        request.currency()
                );
            }
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
            log.error(
                    "Accounts downstream request failed operationId={} operationType={} currency={} status=error errorCategory=downstream_unavailable errorType={} source=cash-service targetService=accounts-service",
                    request.operationId(),
                    operationType(uri),
                    request.currency(),
                    exception.getClass().getSimpleName()
            );
            throw new AccountsClientException("Accounts service request failed", exception);
        }
    }

    private static boolean isRecoverable(Throwable exception) {
        if (exception instanceof AccountsClientException accountsClientException) {
            return accountsClientException.getStatusCode().is5xxServerError();
        }
        return true;
    }

    private AccountsBalanceResponse accountsFallback(Throwable exception) {
        if (exception instanceof AccountsClientException accountsClientException) {
            if (accountsClientException.getStatusCode().is5xxServerError()) {
                log.error(
                        "Accounts downstream retries exhausted status={} errorCode={} errorCategory=downstream_unavailable errorType={} source=cash-service targetService=accounts-service",
                        accountsClientException.getStatusCode().value(),
                        accountsClientException.getCode(),
                        accountsClientException.getClass().getSimpleName()
                );
            }
            throw accountsClientException;
        }
        log.error(
                "Accounts downstream retries exhausted status=error errorCategory=downstream_unavailable errorType={} source=cash-service targetService=accounts-service",
                exception.getClass().getSimpleName()
        );
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

    private String operationType(String uri) {
        return uri.endsWith("/deposit") ? "DEPOSIT" : "WITHDRAW";
    }
}
