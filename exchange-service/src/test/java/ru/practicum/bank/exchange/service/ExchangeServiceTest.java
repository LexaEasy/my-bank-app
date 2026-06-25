package ru.practicum.bank.exchange.service;

import org.junit.jupiter.api.Test;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.exchange.dto.ExchangeRateUpdateRequest;
import ru.practicum.bank.exchange.dto.ExchangeRatesUpdateRequest;
import ru.practicum.bank.exchange.exception.InvalidAmountException;
import ru.practicum.bank.exchange.exception.InvalidRateException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeServiceTest {

    private final ExchangeService exchangeService = new ExchangeService(Clock.fixed(
            Instant.parse("2026-06-25T10:00:00Z"),
            ZoneOffset.UTC
    ));

    @Test
    void shouldReturnInitialRates() {
        var rates = exchangeService.getRates();

        assertThat(rates).hasSize(3);
        assertThat(rates)
                .extracting(rate -> rate.currency().name())
                .containsExactly("RUB", "USD", "CNY");
        assertThat(rates.getFirst().buyRate()).isEqualByComparingTo("1.0000");
        assertThat(rates.getFirst().sellRate()).isEqualByComparingTo("1.0000");
    }

    @Test
    void shouldUpdateNonRubRatesAndKeepRubFixed() {
        exchangeService.updateRates(new ExchangeRatesUpdateRequest(List.of(
                new ExchangeRateUpdateRequest(Currency.RUB, new BigDecimal("2.0000"), new BigDecimal("3.0000")),
                new ExchangeRateUpdateRequest(Currency.USD, new BigDecimal("91.1234"), new BigDecimal("93.1234"))
        )));

        var rub = exchangeService.getRates().getFirst();
        var usd = exchangeService.getRates().stream()
                .filter(rate -> rate.currency() == Currency.USD)
                .findFirst()
                .orElseThrow();

        assertThat(rub.buyRate()).isEqualByComparingTo("1.0000");
        assertThat(rub.sellRate()).isEqualByComparingTo("1.0000");
        assertThat(usd.buyRate()).isEqualByComparingTo("91.1234");
        assertThat(usd.sellRate()).isEqualByComparingTo("93.1234");
    }

    @Test
    void shouldConvertCurrency() {
        var response = exchangeService.convert(Currency.USD, Currency.CNY, new BigDecimal("100.00"));

        assertThat(response.rate()).isEqualByComparingTo("7.419355");
        assertThat(response.targetAmount()).isEqualByComparingTo("741.94");
    }

    @Test
    void shouldRejectInvalidRate() {
        var request = new ExchangeRatesUpdateRequest(List.of(
                new ExchangeRateUpdateRequest(Currency.USD, new BigDecimal("91.12345"), new BigDecimal("93.1234"))
        ));

        assertThatThrownBy(() -> exchangeService.updateRates(request))
                .isInstanceOf(InvalidRateException.class);
    }

    @Test
    void shouldRejectInvalidAmount() {
        assertThatThrownBy(() -> exchangeService.convert(Currency.USD, Currency.CNY, new BigDecimal("100.001")))
                .isInstanceOf(InvalidAmountException.class);
    }
}
