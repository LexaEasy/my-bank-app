package ru.practicum.bank.accounts.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.accounts.dto.AccountResponse;
import ru.practicum.bank.accounts.dto.RecipientResponse;
import ru.practicum.bank.accounts.exception.AccountNotFoundException;
import ru.practicum.bank.accounts.service.AccountService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void shouldReturnCurrentAccount() throws Exception {
        when(accountService.getCurrentAccount("ivan")).thenReturn(new AccountResponse(
                "ivan",
                "Иванов Иван",
                LocalDate.of(1990, 1, 15),
                new BigDecimal("1000.00"),
                "RUB"
        ));

        mockMvc.perform(get("/api/accounts/me").principal(jwtAuthentication("ivan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("ivan"))
                .andExpect(jsonPath("$.name").value("Иванов Иван"))
                .andExpect(jsonPath("$.birthdate").value("1990-01-15"))
                .andExpect(jsonPath("$.balance").value("1000.00"))
                .andExpect(jsonPath("$.currency").value("RUB"));
    }

    @Test
    void shouldUpdateCurrentAccount() throws Exception {
        when(accountService.updateCurrentAccount(any(), any())).thenReturn(new AccountResponse(
                "ivan",
                "Иван Иванов",
                LocalDate.of(1992, 5, 10),
                new BigDecimal("1000.00"),
                "RUB"
        ));

        mockMvc.perform(put("/api/accounts/me")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Иван Иванов",
                                  "birthdate": "1992-05-10"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Иван Иванов"))
                .andExpect(jsonPath("$.birthdate").value("1992-05-10"));

        verify(accountService).updateCurrentAccount(any(), any());
    }

    @Test
    void shouldReturnRecipients() throws Exception {
        when(accountService.getRecipients("ivan")).thenReturn(List.of(
                new RecipientResponse("petr", "Петров Пётр"),
                new RecipientResponse("anna", "Сидорова Анна")
        ));

        mockMvc.perform(get("/api/accounts/recipients").principal(jwtAuthentication("ivan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].login").value("petr"))
                .andExpect(jsonPath("$[1].login").value("anna"));
    }

    @Test
    void shouldReturnValidationError() throws Exception {
        mockMvc.perform(put("/api/accounts/me")
                        .principal(jwtAuthentication("ivan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "A",
                                  "birthdate": "1992-05-10"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldReturnNotFoundError() throws Exception {
        when(accountService.getCurrentAccount("unknown")).thenThrow(new AccountNotFoundException("unknown"));

        mockMvc.perform(get("/api/accounts/me").principal(jwtAuthentication("unknown")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    private JwtAuthenticationToken jwtAuthentication(String login) {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-12T00:00:00Z"))
                .claim("preferred_username", login)
                .build();

        return new JwtAuthenticationToken(jwt);
    }
}
