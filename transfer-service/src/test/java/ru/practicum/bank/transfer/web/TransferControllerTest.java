package ru.practicum.bank.transfer.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.practicum.bank.transfer.client.AccountsClientException;
import ru.practicum.bank.transfer.dto.TransferRequest;
import ru.practicum.bank.transfer.dto.TransferResponse;
import ru.practicum.bank.transfer.exception.OperationBlockedException;
import ru.practicum.bank.transfer.exception.SelfTransferForbiddenException;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.transfer.service.TransferService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransferControllerTest {

    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @Test
    void shouldTransferMoney() throws Exception {
        when(transferService.transfer("ivan", request("olga", "200.00"), IDEMPOTENCY_KEY)).thenReturn(new TransferResponse(
                "ivan",
                "olga",
                new BigDecimal("800.00"),
                "RUB",
                "Transfer completed"
        ));

        mockMvc.perform(postWithIdempotencyKey("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "olga",
                                  "amount": "200.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderLogin").value("ivan"))
                .andExpect(jsonPath("$.recipientLogin").value("olga"))
                .andExpect(jsonPath("$.senderBalance").value("800.00"))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.message").value("Transfer completed"));

        verify(transferService).transfer("ivan", request("olga", "200.00"), IDEMPOTENCY_KEY);
    }

    @Test
    void shouldAcceptCnyTransferRequest() throws Exception {
        when(transferService.transfer("ivan", request("olga", "200.00", Currency.CNY), IDEMPOTENCY_KEY)).thenReturn(new TransferResponse(
                "ivan",
                "olga",
                new BigDecimal("800.00"),
                "CNY",
                "Transfer completed"
        ));

        mockMvc.perform(postWithIdempotencyKey("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "olga",
                                  "amount": "200.00",
                                  "currency": "CNY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("CNY"));

        verify(transferService).transfer("ivan", request("olga", "200.00", Currency.CNY), IDEMPOTENCY_KEY);
    }

    @Test
    void shouldAcceptTargetCurrencyTransferRequest() throws Exception {
        when(transferService.transfer("ivan", new TransferRequest(
                "olga",
                new BigDecimal("100.00"),
                Currency.USD,
                Currency.CNY
        ), IDEMPOTENCY_KEY)).thenReturn(new TransferResponse(
                "ivan",
                "olga",
                new BigDecimal("900.00"),
                "USD",
                "Transfer completed"
        ));

        mockMvc.perform(postWithIdempotencyKey("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "olga",
                                  "amount": "100.00",
                                  "currency": "USD",
                                  "targetCurrency": "CNY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void shouldReturnValidationError() throws Exception {
        mockMvc.perform(postWithIdempotencyKey("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "200.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnUnprocessableEntityForSelfTransfer() throws Exception {
        when(transferService.transfer("ivan", request("ivan", "200.00"), IDEMPOTENCY_KEY))
                .thenThrow(new SelfTransferForbiddenException());

        mockMvc.perform(postWithIdempotencyKey("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "ivan",
                                  "amount": "200.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_TRANSFER_FORBIDDEN"));
    }

    @Test
    void shouldReturnOperationBlockedError() throws Exception {
        when(transferService.transfer("ivan", request("olga", "100000.01"), IDEMPOTENCY_KEY))
                .thenThrow(new OperationBlockedException("Operation amount exceeds blocker limit"));

        mockMvc.perform(postWithIdempotencyKey("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "olga",
                                  "amount": "100000.01",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPERATION_BLOCKED"))
                .andExpect(jsonPath("$.message").value("Operation amount exceeds blocker limit"));
    }

    @Test
    void shouldRejectRequestWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "olga",
                                  "amount": "10.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnConflictForReusedKeyWithDifferentPayload() throws Exception {
        when(transferService.transfer("ivan", request("olga", "20.00"), IDEMPOTENCY_KEY))
                .thenThrow(new AccountsClientException(
                        "Idempotency key was already used with another request",
                        HttpStatus.CONFLICT,
                        null
                ));

        mockMvc.perform(postWithIdempotencyKey("/api/transfers")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientLogin": "olga",
                                  "amount": "20.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    private JwtAuthenticationToken jwtAuthentication(String login) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-13T00:00:00Z"))
                .claim("preferred_username", login)
                .build();

        return new JwtAuthenticationToken(jwt);
    }

    private TransferRequest request(String recipientLogin, String amount) {
        return request(recipientLogin, amount, Currency.RUB);
    }

    private TransferRequest request(String recipientLogin, String amount, Currency currency) {
        return new TransferRequest(recipientLogin, new BigDecimal(amount), currency);
    }

    private MockHttpServletRequestBuilder postWithIdempotencyKey(String uri) {
        return post(uri).header("Idempotency-Key", IDEMPOTENCY_KEY);
    }
}
