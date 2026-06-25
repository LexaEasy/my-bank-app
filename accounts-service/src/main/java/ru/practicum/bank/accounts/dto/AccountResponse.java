package ru.practicum.bank.accounts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountResponse(
        String login,
        String name,
        LocalDate birthdate,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal balance,
        String currency
) {
}
