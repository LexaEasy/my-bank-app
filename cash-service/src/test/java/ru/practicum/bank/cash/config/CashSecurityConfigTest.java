package ru.practicum.bank.cash.config;

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
import ru.practicum.bank.cash.dto.CashOperationResponse;
import ru.practicum.bank.cash.service.CashService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CashSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    @MockitoBean
    private CashService cashService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAllowHealthWithoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectCashEndpointWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/cash/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Требуется авторизация"));
    }

    @Test
    void shouldRejectCashEndpointWithoutWriteRole() throws Exception {
        mockMvc.perform(post("/api/cash/deposit")
                        .with(jwt()
                                .jwt(token -> token.claim("preferred_username", "ivan"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Недостаточно прав для выполнения операции"));
    }

    @Test
    void shouldAllowDepositWithRequiredRoles() throws Exception {
        when(cashService.deposit(any(), any(), any())).thenReturn(new CashOperationResponse(
                new BigDecimal("1250.00"),
                "RUB",
                "Счёт пополнен"
        ));

        mockMvc.perform(post("/api/cash/deposit")
                        .header("Idempotency-Key", "55555555-5555-5555-5555-555555555555")
                        .with(jwt()
                                .jwt(token -> token.claim("preferred_username", "ivan"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_USER"),
                                        new SimpleGrantedAuthority("ROLE_CASH_WRITE")
                                ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cashRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value("1250.00"));
    }

    @Test
    void shouldConvertRealmRolesToAuthorities() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-13T00:00:00Z"))
                .claim("realm_access", Map.of("roles", List.of("USER", "CASH_WRITE")))
                .build();

        var authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_CASH_WRITE");
    }

    private String cashRequest() {
        return """
                {
                  "amount": "250.00",
                  "currency": "RUB"
                }
                """;
    }
}
