package ru.practicum.bank.exchangegenerator.client;

import ru.practicum.bank.common.dto.exchange.ExchangeRatesUpdateRequest;

public interface ExchangeClient {

    void updateRates(ExchangeRatesUpdateRequest request);
}
