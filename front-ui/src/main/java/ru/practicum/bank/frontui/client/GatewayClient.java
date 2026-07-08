package ru.practicum.bank.frontui.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.practicum.bank.common.client.ResilientClientExecutor;
import ru.practicum.bank.common.client.ResilientClientFactory;
import ru.practicum.bank.common.dto.exchange.ExchangeRateResponse;
import ru.practicum.bank.frontui.dto.ApiErrorResponse;
import ru.practicum.bank.frontui.dto.AccountForm;
import ru.practicum.bank.frontui.dto.AccountResponse;
import ru.practicum.bank.frontui.dto.CashForm;
import ru.practicum.bank.frontui.dto.CashOperationRequest;
import ru.practicum.bank.frontui.dto.CashOperationResponse;
import ru.practicum.bank.frontui.dto.RecipientResponse;
import ru.practicum.bank.frontui.dto.TransferForm;
import ru.practicum.bank.frontui.dto.TransferRequest;
import ru.practicum.bank.frontui.dto.TransferResponse;
import ru.practicum.bank.frontui.dto.UpdateAccountRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class GatewayClient {

    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

    private final RestClient accountsClient;
    private final RestClient cashClient;
    private final RestClient transferClient;
    private final RestClient exchangeClient;
    private final ResilientClientExecutor clientExecutor;
    private final ObjectMapper objectMapper;

    @Autowired
    public GatewayClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.services.accounts.base-url}") String accountsBaseUrl,
            @Value("${bank.services.cash.base-url}") String cashBaseUrl,
            @Value("${bank.services.transfer.base-url}") String transferBaseUrl,
            @Value("${bank.services.exchange.base-url}") String exchangeBaseUrl,
            ResilientClientFactory resilientClientFactory,
            ObjectMapper objectMapper
    ) {
        this(
                restClientBuilder,
                accountsBaseUrl,
                cashBaseUrl,
                transferBaseUrl,
                exchangeBaseUrl,
                resilientClientFactory.create("bankServices", GatewayClient::isRecoverable),
                objectMapper
        );
    }

    GatewayClient(
            RestClient.Builder restClientBuilder,
            String accountsBaseUrl,
            String cashBaseUrl,
            String transferBaseUrl,
            String exchangeBaseUrl,
            ResilientClientExecutor clientExecutor,
            ObjectMapper objectMapper
    ) {
        this.clientExecutor = clientExecutor;
        this.objectMapper = objectMapper;
        this.accountsClient = restClientBuilder.clone().baseUrl(accountsBaseUrl).build();
        this.cashClient = restClientBuilder.clone().baseUrl(cashBaseUrl).build();
        this.transferClient = restClientBuilder.clone().baseUrl(transferBaseUrl).build();
        this.exchangeClient = restClientBuilder.clone().baseUrl(exchangeBaseUrl).build();
    }

    public TransferResponse transfer(String accessToken, TransferForm form) {
        return runWithCircuitBreaker(() -> transferWithoutCircuitBreaker(accessToken, form));
    }

    private TransferResponse transferWithoutCircuitBreaker(String accessToken, TransferForm form) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Gateway client request prepared operationType=TRANSFER source=front-ui targetService=bank-gateway");
            }
            return transferClient.post()
                    .uri("/api/transfers")
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("Idempotency-Key", form.idempotencyKey());
                    })
                    .body(new TransferRequest(
                            form.recipientLogin(),
                            form.amount(),
                            form.sourceCurrency(),
                            form.currency()
                    ))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(TransferResponse.class);
        } catch (RestClientException exception) {
            log.error(
                    "Gateway client request failed operationType=TRANSFER status=error errorCategory=downstream_unavailable errorType={} source=front-ui targetService=bank-gateway",
                    exception.getClass().getSimpleName()
            );
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }

    public AccountResponse getAccount(String accessToken) {
        return runWithCircuitBreaker(() -> getAccountWithoutCircuitBreaker(accessToken));
    }

    public List<ExchangeRateResponse> getExchangeRates(String accessToken) {
        return runWithCircuitBreaker(() -> getExchangeRatesWithoutCircuitBreaker(accessToken));
    }

    private List<ExchangeRateResponse> getExchangeRatesWithoutCircuitBreaker(String accessToken) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Gateway client request prepared operationType=LOAD_EXCHANGE_RATES source=front-ui targetService=bank-gateway");
            }
            ExchangeRateResponse[] rates = exchangeClient.get()
                    .uri("/api/exchange/rates")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(ExchangeRateResponse[].class);
            return rates == null ? List.of() : Arrays.asList(rates);
        } catch (RestClientException exception) {
            log.error(
                    "Gateway client request failed operationType=LOAD_EXCHANGE_RATES status=error errorCategory=downstream_unavailable errorType={} source=front-ui targetService=bank-gateway",
                    exception.getClass().getSimpleName()
            );
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }

    private AccountResponse getAccountWithoutCircuitBreaker(String accessToken) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Gateway client request prepared operationType=LOAD_ACCOUNT source=front-ui targetService=bank-gateway");
            }
            return accountsClient.get()
                    .uri("/api/accounts/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(AccountResponse.class);
        } catch (RestClientException exception) {
            log.error(
                    "Gateway client request failed operationType=LOAD_ACCOUNT status=error errorCategory=downstream_unavailable errorType={} source=front-ui targetService=bank-gateway",
                    exception.getClass().getSimpleName()
            );
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }

    public AccountResponse updateAccount(String accessToken, AccountForm form) {
        return runWithCircuitBreaker(() -> updateAccountWithoutCircuitBreaker(accessToken, form));
    }

    private AccountResponse updateAccountWithoutCircuitBreaker(String accessToken, AccountForm form) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Gateway client request prepared operationType=UPDATE_PROFILE source=front-ui targetService=bank-gateway");
            }
            return accountsClient.put()
                    .uri("/api/accounts/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .body(new UpdateAccountRequest(form.name(), form.birthdate()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(AccountResponse.class);
        } catch (RestClientException exception) {
            log.error(
                    "Gateway client request failed operationType=UPDATE_PROFILE status=error errorCategory=downstream_unavailable errorType={} source=front-ui targetService=bank-gateway",
                    exception.getClass().getSimpleName()
            );
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }

    public List<RecipientResponse> getRecipients(String accessToken) {
        return runWithCircuitBreaker(() -> getRecipientsWithoutCircuitBreaker(accessToken));
    }

    private List<RecipientResponse> getRecipientsWithoutCircuitBreaker(String accessToken) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Gateway client request prepared operationType=LOAD_RECIPIENTS source=front-ui targetService=bank-gateway");
            }
            RecipientResponse[] recipients = accountsClient.get()
                    .uri("/api/accounts/recipients")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(RecipientResponse[].class);
            return recipients == null ? List.of() : Arrays.asList(recipients);
        } catch (RestClientException exception) {
            log.error(
                    "Gateway client request failed operationType=LOAD_RECIPIENTS status=error errorCategory=downstream_unavailable errorType={} source=front-ui targetService=bank-gateway",
                    exception.getClass().getSimpleName()
            );
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }

    public CashOperationResponse deposit(String accessToken, CashForm form) {
        return cashOperation(accessToken, "/api/cash/deposit", form);
    }

    public CashOperationResponse withdraw(String accessToken, CashForm form) {
        return cashOperation(accessToken, "/api/cash/withdraw", form);
    }

    private CashOperationResponse cashOperation(String accessToken, String uri, CashForm form) {
        return runWithCircuitBreaker(() -> cashOperationWithoutCircuitBreaker(accessToken, uri, form));
    }

    private CashOperationResponse cashOperationWithoutCircuitBreaker(String accessToken, String uri, CashForm form) {
        try {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Gateway client request prepared operationType={} source=front-ui targetService=bank-gateway",
                        cashOperationType(uri)
                );
            }
            return cashClient.post()
                    .uri(uri)
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("Idempotency-Key", form.idempotencyKey());
                    })
                    .body(new CashOperationRequest(form.amount(), form.currency()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(CashOperationResponse.class);
        } catch (RestClientException exception) {
            log.error(
                    "Gateway client request failed operationType={} status=error errorCategory=downstream_unavailable errorType={} source=front-ui targetService=bank-gateway",
                    cashOperationType(uri),
                    exception.getClass().getSimpleName()
            );
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }

    private <T> T runWithCircuitBreaker(ClientCall<T> call) {
        return clientExecutor.execute(call::execute, this::gatewayFallback);
    }

    static boolean isRecoverable(Throwable exception) {
        return !(exception instanceof GatewayClientException gatewayClientException)
                || gatewayClientException.isTechnical();
    }

    private <T> T gatewayFallback(Throwable exception) {
        if (exception instanceof GatewayClientException gatewayClientException) {
            throw gatewayClientException;
        }
        log.error(
                "Gateway client retries exhausted status=error errorCategory=downstream_unavailable errorType={} source=front-ui targetService=bank-gateway",
                exception.getClass().getSimpleName()
        );
        throw new GatewayClientException("Банковские сервисы временно недоступны", exception);
    }

    private void handleError(ClientHttpResponse response) throws IOException {
        byte[] body = response.getBody().readAllBytes();
        String message = extractMessage(body);
        if (message != null) {
            throw new GatewayClientException(message);
        }
        throw new GatewayClientException("Gateway request failed: " + response.getStatusCode());
    }

    private String extractMessage(byte[] body) {
        if (body.length == 0) {
            return null;
        }
        try {
            ApiErrorResponse error = objectMapper.readValue(body, ApiErrorResponse.class);
            if (error.message() != null && !error.message().isBlank()) {
                return error.message();
            }
        } catch (IOException ignored) {
            // Fall back to the HTTP status when Gateway does not return the expected error body.
        }
        return null;
    }

    private String cashOperationType(String uri) {
        return uri.endsWith("/deposit") ? "DEPOSIT" : "WITHDRAW";
    }

    @FunctionalInterface
    private interface ClientCall<T> {
        T execute();
    }
}
