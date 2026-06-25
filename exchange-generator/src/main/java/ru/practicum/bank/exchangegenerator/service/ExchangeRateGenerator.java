package ru.practicum.bank.exchangegenerator.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.common.dto.exchange.ExchangeRateUpdateRequest;
import ru.practicum.bank.common.dto.exchange.ExchangeRatesUpdateRequest;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ExchangeRateGenerator {

    private final List<RateStep> usdSteps = List.of(
            new RateStep("90.0000", "92.0000"),
            new RateStep("91.0000", "93.0000"),
            new RateStep("89.5000", "91.5000")
    );
    private final List<RateStep> cnySteps = List.of(
            new RateStep("12.4000", "12.8000"),
            new RateStep("12.5000", "12.9000"),
            new RateStep("12.3000", "12.7000")
    );

    private int index;

    public synchronized ExchangeRatesUpdateRequest nextRates() {
        RateStep usd = usdSteps.get(index % usdSteps.size());
        RateStep cny = cnySteps.get(index % cnySteps.size());
        index++;

        return new ExchangeRatesUpdateRequest(List.of(
                new ExchangeRateUpdateRequest(Currency.RUB, new BigDecimal("1.0000"), new BigDecimal("1.0000")),
                toRequest(Currency.USD, usd),
                toRequest(Currency.CNY, cny)
        ));
    }

    private ExchangeRateUpdateRequest toRequest(Currency currency, RateStep step) {
        return new ExchangeRateUpdateRequest(
                currency,
                new BigDecimal(step.buyRate()),
                new BigDecimal(step.sellRate())
        );
    }
}
