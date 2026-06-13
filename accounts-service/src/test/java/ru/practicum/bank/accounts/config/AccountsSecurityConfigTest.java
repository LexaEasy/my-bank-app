package ru.practicum.bank.accounts.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.accounts.dto.AccountResponse;
import ru.practicum.bank.accounts.service.AccountService;
import ru.practicum.bank.accounts.service.BalanceService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountsSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private BalanceService balanceService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAllowHealthWithoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectAccountEndpointWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAccountEndpointWithJwt() throws Exception {
        when(accountService.getCurrentAccount("ivan")).thenReturn(new AccountResponse(
                "ivan",
                "Иванов Иван",
                LocalDate.of(1990, 1, 15),
                new BigDecimal("1000.00"),
                "RUB"
        ));

        mockMvc.perform(get("/api/accounts/me").with(jwt().jwt(token -> token
                        .claim("preferred_username", "ivan")
                        .claim("realm_access", Map.of("roles", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("ivan"));
    }
}
