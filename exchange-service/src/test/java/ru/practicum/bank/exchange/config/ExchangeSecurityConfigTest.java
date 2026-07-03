package ru.practicum.bank.exchange.config;

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
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.dto.exchange.ExchangeRateResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.exchange.service.ExchangeService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExchangeSecurityConfigTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-06-25T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

    @MockitoBean
    private ExchangeService exchangeService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAllowRatesWithoutJwt() throws Exception {
        when(exchangeService.getRates()).thenReturn(List.of(rate(Currency.USD, "90.0000", "92.0000")));

        mockMvc.perform(get("/api/exchange/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    @Test
    void shouldAllowConversionWithoutJwt() throws Exception {
        when(exchangeService.convert(Currency.USD, Currency.CNY, new BigDecimal("100.00")))
                .thenReturn(new ConversionResponse(
                        Currency.USD,
                        Currency.CNY,
                        new BigDecimal("100.00"),
                        new BigDecimal("741.94"),
                        new BigDecimal("7.419355"),
                        UPDATED_AT
                ));

        mockMvc.perform(get("/api/exchange/conversion")
                        .param("sourceCurrency", "USD")
                        .param("targetCurrency", "CNY")
                        .param("amount", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetCurrency").value("CNY"));
    }

    @Test
    void shouldRejectRateUpdateWithoutJwt() throws Exception {
        mockMvc.perform(put("/api/exchange/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Требуется авторизация"));
    }

    @Test
    void shouldRejectRateUpdateWithoutServiceRole() throws Exception {
        mockMvc.perform(put("/api/exchange/rates")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Недостаточно прав для выполнения операции"));
    }

    @Test
    void shouldAllowRateUpdateForExchangeGeneratorService() throws Exception {
        when(exchangeService.updateRates(any())).thenReturn(List.of(rate(Currency.USD, "91.0000", "93.0000")));

        mockMvc.perform(put("/api/exchange/rates")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_SERVICE"),
                                new SimpleGrantedAuthority("ROLE_EXCHANGE_GENERATOR")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    @Test
    void shouldConvertRealmRolesToAuthorities() {
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.parse("2026-06-13T00:00:00Z"))
                .claim("realm_access", Map.of("roles", List.of("SERVICE", "EXCHANGE_GENERATOR")))
                .build();

        var authentication = jwtAuthenticationConverter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_SERVICE", "ROLE_EXCHANGE_GENERATOR");
    }

    private ExchangeRateResponse rate(Currency currency, String buyRate, String sellRate) {
        return new ExchangeRateResponse(
                currency,
                new BigDecimal(buyRate),
                new BigDecimal(sellRate),
                UPDATED_AT
        );
    }

    private String updateRequest() {
        return """
                {
                  "rates": [
                    {
                      "currency": "USD",
                      "buyRate": "91.0000",
                      "sellRate": "93.0000"
                    }
                  ]
                }
                """;
    }
}
