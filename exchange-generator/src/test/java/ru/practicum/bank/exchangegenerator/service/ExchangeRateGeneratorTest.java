package ru.practicum.bank.exchangegenerator.service;

import org.junit.jupiter.api.Test;
import ru.practicum.bank.common.model.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeRateGeneratorTest {

    private final ExchangeRateGenerator generator = new ExchangeRateGenerator();

    @Test
    void shouldGenerateRubUsdAndCnyRates() {
        var request = generator.nextRates();

        assertThat(request.rates())
                .extracting(rate -> rate.currency().name())
                .containsExactly("RUB", "USD", "CNY");
        assertThat(request.rates().getFirst().buyRate()).isEqualByComparingTo("1.0000");
        assertThat(request.rates().getFirst().sellRate()).isEqualByComparingTo("1.0000");
    }

    @Test
    void shouldMoveThroughDeterministicRoundRobinSteps() {
        var first = generator.nextRates();
        var second = generator.nextRates();

        assertThat(first.rates().get(1).currency()).isEqualTo(Currency.USD);
        assertThat(first.rates().get(1).buyRate()).isEqualByComparingTo("90.0000");
        assertThat(second.rates().get(1).buyRate()).isEqualByComparingTo("91.0000");
    }
}
