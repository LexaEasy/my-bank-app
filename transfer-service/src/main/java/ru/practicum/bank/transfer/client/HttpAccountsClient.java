package ru.practicum.bank.transfer.client;

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
import ru.practicum.bank.common.client.ResilientClientExecutor;
import ru.practicum.bank.common.client.ResilientClientFactory;
import ru.practicum.bank.transfer.dto.ApiErrorResponse;
import ru.practicum.bank.transfer.service.TransferExecutor;
import ru.practicum.bank.transfer.service.TransferOperation;
import ru.practicum.bank.transfer.service.TransferResult;

@Component
public class HttpAccountsClient implements TransferExecutor {

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
    public TransferResult execute(TransferOperation operation) {
        return clientExecutor.execute(
                () -> executeWithoutCircuitBreaker(operation),
                this::accountsFallback
        );
    }

    private TransferResult executeWithoutCircuitBreaker(TransferOperation operation) {
        try {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Accounts downstream request prepared operationId={} operationType=TRANSFER currency={} source=transfer-service targetService=accounts-service",
                        operation.operationId(),
                        operation.currency()
                );
            }
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
            var error = extractError(exception);
            throw new AccountsClientException(
                    error.message(),
                    exception.getStatusCode(),
                    error.code(),
                    exception
            );
        } catch (RestClientException exception) {
            log.error(
                    "Accounts downstream request failed operationId={} operationType=TRANSFER currency={} status=error errorCategory=downstream_unavailable errorType={} source=transfer-service targetService=accounts-service",
                    operation.operationId(),
                    operation.currency(),
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

    private TransferResult accountsFallback(Throwable exception) {
        if (exception instanceof AccountsClientException accountsClientException) {
            if (accountsClientException.getStatusCode().is5xxServerError()) {
                log.error(
                        "Accounts downstream retries exhausted status={} errorCode={} errorCategory=downstream_unavailable errorType={} source=transfer-service targetService=accounts-service",
                        accountsClientException.getStatusCode().value(),
                        accountsClientException.getCode(),
                        accountsClientException.getClass().getSimpleName()
                );
            }
            throw accountsClientException;
        }
        log.error(
                "Accounts downstream retries exhausted status=error errorCategory=downstream_unavailable errorType={} source=transfer-service targetService=accounts-service",
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

    private AccountsTransferRequest toAccountsRequest(TransferOperation operation) {
        return new AccountsTransferRequest(
                operation.senderLogin(),
                operation.recipientLogin(),
                operation.amount(),
                operation.currency(),
                operation.recipientAmount(),
                operation.recipientCurrency(),
                operation.operationId()
        );
    }
}
