package ru.practicum.bank.cash.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.practicum.bank.cash.client.AccountsClientException;
import ru.practicum.bank.cash.exception.OperationBlockedException;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.dto.CashOperationResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.cash.service.CashService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CashController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CashControllerTest.MetricsTestConfiguration.class)
class CashControllerTest {

    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CashService cashService;

    @Test
    void shouldDepositMoney() throws Exception {
        when(cashService.deposit("ivan", request("250.00"), IDEMPOTENCY_KEY)).thenReturn(new CashOperationResponse(
                new BigDecimal("1250.00"),
                "RUB",
                "Счёт пополнен"
        ));

        mockMvc.perform(postWithIdempotencyKey("/api/cash/deposit")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "250.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("1250.00"))
                .andExpect(jsonPath("$.currency").value("RUB"))
                .andExpect(jsonPath("$.message").value("Счёт пополнен"));

        verify(cashService).deposit("ivan", request("250.00"), IDEMPOTENCY_KEY);
    }

    @Test
    void shouldAcceptUsdDepositRequest() throws Exception {
        when(cashService.deposit("ivan", request("50.00", Currency.USD), IDEMPOTENCY_KEY)).thenReturn(new CashOperationResponse(
                new BigDecimal("1050.00"),
                "USD",
                "Счёт пополнен"
        ));

        mockMvc.perform(postWithIdempotencyKey("/api/cash/deposit")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "50.00",
                                  "currency": "USD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"));

        verify(cashService).deposit("ivan", request("50.00", Currency.USD), IDEMPOTENCY_KEY);
    }

    @Test
    void shouldWithdrawMoney() throws Exception {
        when(cashService.withdraw("ivan", request("100.00"), IDEMPOTENCY_KEY)).thenReturn(new CashOperationResponse(
                new BigDecimal("900.00"),
                "RUB",
                "Деньги сняты со счёта"
        ));

        mockMvc.perform(postWithIdempotencyKey("/api/cash/withdraw")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "100.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("900.00"))
                .andExpect(jsonPath("$.message").value("Деньги сняты со счёта"));

        verify(cashService).withdraw("ivan", request("100.00"), IDEMPOTENCY_KEY);
    }

    @Test
    void shouldReturnValidationError() throws Exception {
        mockMvc.perform(postWithIdempotencyKey("/api/cash/deposit")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnOperationBlockedError() throws Exception {
        when(cashService.deposit("ivan", request("100000.01"), IDEMPOTENCY_KEY))
                .thenThrow(new OperationBlockedException("Operation amount exceeds blocker limit"));

        mockMvc.perform(postWithIdempotencyKey("/api/cash/deposit")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
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
        mockMvc.perform(post("/api/cash/deposit")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "10.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnConflictForReusedKeyWithDifferentPayload() throws Exception {
        when(cashService.deposit("ivan", request("20.00"), IDEMPOTENCY_KEY))
                .thenThrow(new AccountsClientException(
                        "Idempotency key was already used with another request",
                        HttpStatus.CONFLICT,
                        null
                ));

        mockMvc.perform(postWithIdempotencyKey("/api/cash/deposit")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "20.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void shouldPreserveAccountsBusinessErrorStatusAndCode() throws Exception {
        when(cashService.withdraw("ivan", request("999999.00"), IDEMPOTENCY_KEY))
                .thenThrow(new AccountsClientException(
                        "Недостаточно средств",
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "INSUFFICIENT_FUNDS",
                        null
                ));

        mockMvc.perform(postWithIdempotencyKey("/api/cash/withdraw")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": "999999.00",
                                  "currency": "RUB"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message").value("Недостаточно средств"));
    }

    private JwtAuthenticationToken jwtAuthentication(String login) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-13T00:00:00Z"))
                .claim("preferred_username", login)
                .build();

        return new JwtAuthenticationToken(jwt);
    }

    private CashOperationRequest request(String amount) {
        return request(amount, Currency.RUB);
    }

    private CashOperationRequest request(String amount, Currency currency) {
        return new CashOperationRequest(new BigDecimal(amount), currency);
    }

    private MockHttpServletRequestBuilder postWithIdempotencyKey(String uri) {
        return post(uri).header("Idempotency-Key", IDEMPOTENCY_KEY);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MetricsTestConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
