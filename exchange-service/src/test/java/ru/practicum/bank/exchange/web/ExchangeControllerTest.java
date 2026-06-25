package ru.practicum.bank.exchange.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.exchange.dto.ExchangeRateResponse;
import ru.practicum.bank.exchange.exception.InvalidRateException;
import ru.practicum.bank.exchange.service.ExchangeService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExchangeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExchangeControllerTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-06-25T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExchangeService exchangeService;

    @Test
    void shouldReturnRates() throws Exception {
        when(exchangeService.getRates()).thenReturn(List.of(
                rate(Currency.RUB, "1.0000", "1.0000"),
                rate(Currency.USD, "90.0000", "92.0000")
        ));

        mockMvc.perform(get("/api/exchange/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("RUB"))
                .andExpect(jsonPath("$[0].buyRate").value("1.0000"))
                .andExpect(jsonPath("$[1].currency").value("USD"));
    }

    @Test
    void shouldUpdateRates() throws Exception {
        when(exchangeService.updateRates(any())).thenReturn(List.of(
                rate(Currency.USD, "91.0000", "93.0000")
        ));

        mockMvc.perform(put("/api/exchange/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rates": [
                                    {
                                      "currency": "USD",
                                      "buyRate": "91.0000",
                                      "sellRate": "93.0000"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].buyRate").value("91.0000"));

        verify(exchangeService).updateRates(any());
    }

    @Test
    void shouldConvertCurrency() throws Exception {
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
                .andExpect(jsonPath("$.sourceCurrency").value("USD"))
                .andExpect(jsonPath("$.targetCurrency").value("CNY"))
                .andExpect(jsonPath("$.targetAmount").value("741.94"));
    }

    @Test
    void shouldReturnInvalidRateError() throws Exception {
        when(exchangeService.updateRates(any())).thenThrow(new InvalidRateException());

        mockMvc.perform(put("/api/exchange/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rates": [
                                    {
                                      "currency": "USD",
                                      "buyRate": "91.12345",
                                      "sellRate": "93.0000"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RATE"));
    }

    private ExchangeRateResponse rate(Currency currency, String buyRate, String sellRate) {
        return new ExchangeRateResponse(
                currency,
                new BigDecimal(buyRate),
                new BigDecimal(sellRate),
                UPDATED_AT
        );
    }
}
