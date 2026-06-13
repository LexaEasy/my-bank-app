package ru.practicum.bank.cash.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.dto.CashOperationResponse;
import ru.practicum.bank.cash.model.Currency;
import ru.practicum.bank.cash.service.CashService;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CashController.class)
@AutoConfigureMockMvc(addFilters = false)
class CashControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CashService cashService;

    @Test
    void shouldDepositMoney() throws Exception {
        when(cashService.deposit("ivan", request("250.00"))).thenReturn(new CashOperationResponse(
                new BigDecimal("1250.00"),
                "RUB",
                "Счёт пополнен"
        ));

        mockMvc.perform(post("/api/cash/deposit")
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

        verify(cashService).deposit("ivan", request("250.00"));
    }

    @Test
    void shouldWithdrawMoney() throws Exception {
        when(cashService.withdraw("ivan", request("100.00"))).thenReturn(new CashOperationResponse(
                new BigDecimal("900.00"),
                "RUB",
                "Деньги сняты со счёта"
        ));

        mockMvc.perform(post("/api/cash/withdraw")
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

        verify(cashService).withdraw("ivan", request("100.00"));
    }

    @Test
    void shouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/api/cash/deposit")
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

    private JwtAuthenticationToken jwtAuthentication(String login) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-13T00:00:00Z"))
                .claim("preferred_username", login)
                .build();

        return new JwtAuthenticationToken(jwt);
    }

    private CashOperationRequest request(String amount) {
        return new CashOperationRequest(new BigDecimal(amount), Currency.RUB);
    }
}
