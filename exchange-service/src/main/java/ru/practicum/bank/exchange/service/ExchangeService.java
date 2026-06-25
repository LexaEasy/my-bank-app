package ru.practicum.bank.exchange.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.dto.exchange.ExchangeRateUpdateRequest;
import ru.practicum.bank.common.dto.exchange.ExchangeRatesUpdateRequest;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.exchange.dto.ExchangeRateResponse;
import ru.practicum.bank.exchange.exception.InvalidAmountException;
import ru.practicum.bank.exchange.exception.InvalidRateException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ExchangeService {

    private static final BigDecimal RUB_RATE = new BigDecimal("1.0000");
    private static final int RATE_SCALE = 4;
    private static final int AMOUNT_SCALE = 2;

    private final Clock clock;
    private final Map<Currency, ExchangeRateSnapshot> rates = new EnumMap<>(Currency.class);

    public ExchangeService(Clock clock) {
        this.clock = clock;
        Instant now = clock.instant();
        rates.put(Currency.RUB, snapshot(Currency.RUB, RUB_RATE, RUB_RATE, now));
        rates.put(Currency.USD, snapshot(Currency.USD, "90.0000", "92.0000", now));
        rates.put(Currency.CNY, snapshot(Currency.CNY, "12.4000", "12.8000", now));
    }

    public synchronized List<ExchangeRateResponse> getRates() {
        return rates.values().stream()
                .sorted(Comparator.comparing(ExchangeRateSnapshot::currency))
                .map(this::toResponse)
                .toList();
    }

    public synchronized List<ExchangeRateResponse> updateRates(ExchangeRatesUpdateRequest request) {
        Instant updatedAt = clock.instant();
        for (ExchangeRateUpdateRequest rate : request.rates()) {
            if (rate.currency() == Currency.RUB) {
                rates.put(Currency.RUB, snapshot(Currency.RUB, RUB_RATE, RUB_RATE, updatedAt));
                continue;
            }
            validateRate(rate.buyRate());
            validateRate(rate.sellRate());
            rates.put(
                    rate.currency(),
                    snapshot(rate.currency(), normalizeRate(rate.buyRate()), normalizeRate(rate.sellRate()), updatedAt)
            );
        }

        return getRates();
    }

    public synchronized ConversionResponse convert(Currency sourceCurrency, Currency targetCurrency, BigDecimal amount) {
        validateAmount(amount);

        ExchangeRateSnapshot sourceRate = rates.get(sourceCurrency);
        ExchangeRateSnapshot targetRate = rates.get(targetCurrency);
        BigDecimal conversionRate = sourceRate.sellRate()
                .divide(targetRate.buyRate(), 6, RoundingMode.HALF_UP);
        BigDecimal targetAmount = amount.multiply(conversionRate)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        Instant updatedAt = sourceRate.updatedAt().isAfter(targetRate.updatedAt())
                ? sourceRate.updatedAt()
                : targetRate.updatedAt();

        return new ConversionResponse(
                sourceCurrency,
                targetCurrency,
                amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY),
                targetAmount,
                conversionRate,
                updatedAt
        );
    }

    private ExchangeRateResponse toResponse(ExchangeRateSnapshot snapshot) {
        return new ExchangeRateResponse(
                snapshot.currency(),
                snapshot.buyRate(),
                snapshot.sellRate(),
                snapshot.updatedAt()
        );
    }

    private ExchangeRateSnapshot snapshot(Currency currency, String buyRate, String sellRate, Instant updatedAt) {
        return snapshot(currency, new BigDecimal(buyRate), new BigDecimal(sellRate), updatedAt);
    }

    private ExchangeRateSnapshot snapshot(Currency currency, BigDecimal buyRate, BigDecimal sellRate, Instant updatedAt) {
        return new ExchangeRateSnapshot(currency, buyRate, sellRate, updatedAt);
    }

    private void validateRate(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.scale() > RATE_SCALE) {
            throw new InvalidRateException();
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.scale() > AMOUNT_SCALE) {
            throw new InvalidAmountException();
        }
    }

    private BigDecimal normalizeRate(BigDecimal rate) {
        return rate.setScale(RATE_SCALE, RoundingMode.UNNECESSARY);
    }
}
