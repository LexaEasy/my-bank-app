package ru.practicum.bank.transfer.client;

import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

public interface ExchangeClient {

    ConversionResponse convert(Currency sourceCurrency, Currency targetCurrency, BigDecimal amount);
}
