package ru.practicum.bank.accounts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record BalanceResponse(
        String login,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal balance,

        String currency
) {
}
