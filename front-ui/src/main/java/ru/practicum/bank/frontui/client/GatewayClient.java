package ru.practicum.bank.frontui.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final SimpleCircuitBreaker circuitBreaker;

    @Autowired
    public GatewayClient(
            RestClient.Builder restClientBuilder,
            @Value("${bank.gateway.base-url}") String gatewayBaseUrl
    ) {
        this(restClientBuilder, gatewayBaseUrl, SimpleCircuitBreaker.withDefaults("bankGateway"));
    }

    GatewayClient(
            RestClient.Builder restClientBuilder,
            String gatewayBaseUrl,
            SimpleCircuitBreaker circuitBreaker
    ) {
        this.circuitBreaker = circuitBreaker;
        this.restClient = restClientBuilder
                .baseUrl(gatewayBaseUrl)
                .build();
    }

    public TransferResponse transfer(String accessToken, TransferForm form) {
        return runWithCircuitBreaker(() -> transferWithoutCircuitBreaker(accessToken, form));
    }

    private TransferResponse transferWithoutCircuitBreaker(String accessToken, TransferForm form) {
        try {
            return restClient.post()
                    .uri("/api/transfers")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .body(new TransferRequest(form.recipientLogin(), form.amount(), form.currency()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(TransferResponse.class);
        } catch (RestClientException exception) {
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }

    public AccountResponse getAccount(String accessToken) {
        return runWithCircuitBreaker(() -> getAccountWithoutCircuitBreaker(accessToken));
    }

    private AccountResponse getAccountWithoutCircuitBreaker(String accessToken) {
        try {
            return restClient.get()
                    .uri("/api/accounts/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(AccountResponse.class);
        } catch (RestClientException exception) {
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }

    public AccountResponse updateAccount(String accessToken, AccountForm form) {
        return runWithCircuitBreaker(() -> updateAccountWithoutCircuitBreaker(accessToken, form));
    }

    private AccountResponse updateAccountWithoutCircuitBreaker(String accessToken, AccountForm form) {
        try {
            return restClient.put()
                    .uri("/api/accounts/me")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .body(new UpdateAccountRequest(form.name(), form.birthdate()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(AccountResponse.class);
        } catch (RestClientException exception) {
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }

    public List<RecipientResponse> getRecipients(String accessToken) {
        return runWithCircuitBreaker(() -> getRecipientsWithoutCircuitBreaker(accessToken));
    }

    private List<RecipientResponse> getRecipientsWithoutCircuitBreaker(String accessToken) {
        try {
            RecipientResponse[] recipients = restClient.get()
                    .uri("/api/accounts/recipients")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(RecipientResponse[].class);
            return recipients == null ? List.of() : Arrays.asList(recipients);
        } catch (RestClientException exception) {
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
            return restClient.post()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .body(new CashOperationRequest(form.amount(), form.currency()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> handleError(response))
                    .body(CashOperationResponse.class);
        } catch (RestClientException exception) {
            throw new GatewayClientException("Gateway request failed", exception);
        }
    }

    private <T> T runWithCircuitBreaker(ClientCall<T> call) {
        return circuitBreaker.execute(call::execute, this::gatewayFallback);
    }

    private <T> T gatewayFallback(Throwable exception) {
        if (exception instanceof GatewayClientException gatewayClientException) {
            throw gatewayClientException;
        }
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
            ApiErrorResponse error = OBJECT_MAPPER.readValue(body, ApiErrorResponse.class);
            if (error.message() != null && !error.message().isBlank()) {
                return error.message();
            }
        } catch (IOException ignored) {
            // Fall back to the HTTP status when Gateway does not return the expected error body.
        }
        return null;
    }

    @FunctionalInterface
    private interface ClientCall<T> {
        T execute();
    }
}
