package ru.practicum.bank.blocker.config;

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
import ru.practicum.bank.blocker.service.BlockerService;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;

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

@SpringBootTest(properties = "management.endpoint.health.probes.enabled=true")
@AutoConfigureMockMvc
class BlockerSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    @MockitoBean
    private BlockerService blockerService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAllowHealthWithoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectBlockerEndpointWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/blocker/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectBlockerEndpointForRegularUser() throws Exception {
        mockMvc.perform(post("/api/blocker/check")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAllowBlockerEndpointForService() throws Exception {
        when(blockerService.check(any())).thenReturn(new OperationCheckResponse(true, null));

        mockMvc.perform(post("/api/blocker/check")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(operationRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));
    }

    @Test
    void shouldConvertRealmRolesToAuthorities() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-13T00:00:00Z"))
                .claim("realm_access", Map.of("roles", List.of("SERVICE")))
                .build();

        var authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SERVICE");
    }

    private String operationRequest() {
        return """
                {
                  "operationId": "op-1",
                  "operationType": "DEPOSIT",
                  "login": "ivan",
                  "amount": "1000.00",
                  "currency": "RUB",
                  "normalizedAmount": "1000.00",
                  "baseCurrency": "RUB"
                }
                """;
    }
}
