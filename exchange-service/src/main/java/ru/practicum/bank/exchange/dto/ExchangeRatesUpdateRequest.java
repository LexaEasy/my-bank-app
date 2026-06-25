package ru.practicum.bank.exchange.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ExchangeRatesUpdateRequest(
        @NotEmpty
        List<@Valid ExchangeRateUpdateRequest> rates
) {
}
