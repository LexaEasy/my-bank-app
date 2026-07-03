package ru.practicum.bank.transfer.config;

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
import ru.practicum.bank.transfer.dto.TransferResponse;
import ru.practicum.bank.transfer.service.TransferService;

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
class TransferSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    @MockitoBean
    private TransferService transferService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAllowHealthWithoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectTransferEndpointWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authorization is required"));
    }

    @Test
    void shouldRejectTransferEndpointWithoutWriteRole() throws Exception {
        mockMvc.perform(post("/api/transfers")
                        .with(jwt()
                                .jwt(token -> token.claim("preferred_username", "ivan"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Not enough permissions"));
    }

    @Test
    void shouldAllowTransferWithRequiredRoles() throws Exception {
        when(transferService.transfer(any(), any(), any())).thenReturn(new TransferResponse(
                "ivan",
                "olga",
                new BigDecimal("800.00"),
                "RUB",
                "Transfer completed"
        ));

        mockMvc.perform(post("/api/transfers")
                        .header("Idempotency-Key", "66666666-6666-6666-6666-666666666666")
                        .with(jwt()
                                .jwt(token -> token.claim("preferred_username", "ivan"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_USER"),
                                        new SimpleGrantedAuthority("ROLE_TRANSFER_WRITE")
                                ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderBalance").value("800.00"));
    }

    @Test
    void shouldConvertRealmRolesToAuthorities() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-13T00:00:00Z"))
                .claim("realm_access", Map.of("roles", List.of("USER", "TRANSFER_WRITE")))
                .build();

        var authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_TRANSFER_WRITE");
    }

    private String transferRequest() {
        return """
                {
                  "recipientLogin": "olga",
                  "amount": "200.00",
                  "currency": "RUB"
                }
                """;
    }
}
