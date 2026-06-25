package ru.practicum.bank.accounts.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.accounts.dto.AccountResponse;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.service.AccountService;
import ru.practicum.bank.accounts.service.BalanceService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    @Test
    void shouldAllowHealthWithoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectAccountEndpointWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Требуется авторизация"));
    }

    @Test
    void shouldAllowReadEndpointWithRequiredRoles() throws Exception {
        when(accountService.getCurrentAccount("ivan")).thenReturn(new AccountResponse(
                "ivan",
                "Иванов Иван",
                LocalDate.of(1990, 1, 15),
                new BigDecimal("1000.00"),
                "RUB"
        ));

        mockMvc.perform(get("/api/accounts/me").with(jwt()
                        .jwt(token -> token.claim("preferred_username", "ivan"))
                        .authorities(
                                new SimpleGrantedAuthority("ROLE_USER"),
                                new SimpleGrantedAuthority("ROLE_ACCOUNTS_READ")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("ivan"));
    }

    @Test
    void shouldRejectReadEndpointWithoutAccountsReadRole() throws Exception {
        mockMvc.perform(get("/api/accounts/me").with(jwt()
                        .jwt(token -> token.claim("preferred_username", "ivan"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Недостаточно прав для выполнения операции"));
    }

    @Test
    void shouldAllowWriteEndpointWithRequiredRoles() throws Exception {
        when(accountService.updateCurrentAccount(any(), any())).thenReturn(new AccountResponse(
                "ivan",
                "Иван Иванов",
                LocalDate.of(1992, 5, 10),
                new BigDecimal("1000.00"),
                "RUB"
        ));

        mockMvc.perform(put("/api/accounts/me")
                        .with(jwt()
                                .jwt(token -> token.claim("preferred_username", "ivan"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_USER"),
                                        new SimpleGrantedAuthority("ROLE_ACCOUNTS_WRITE")
                                ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Иван Иванов",
                                  "birthdate": "1992-05-10"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Иван Иванов"));
    }

    @Test
    void shouldAllowInternalEndpointWithServiceRoles() throws Exception {
        when(balanceService.deposit(any())).thenReturn(new BalanceResponse(
                "ivan",
                new BigDecimal("1250.00"),
                "RUB"
        ));

        mockMvc.perform(post("/api/accounts/internal/balance/deposit")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_SERVICE"),
                                new SimpleGrantedAuthority("ROLE_ACCOUNTS_INTERNAL")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login": "ivan",
                                  "amount": "250.00",
                                  "currency": "RUB",
                                  "operationId": "operation-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("1250.00"));
    }

    @Test
    void shouldConvertRealmRolesToAuthorities() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-13T00:00:00Z"))
                .claim("realm_access", Map.of("roles", List.of("USER", "ACCOUNTS_READ")))
                .build();

        var authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ACCOUNTS_READ");
    }
}
