package ru.practicum.bank.accounts.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateAccountRequest(
        @NotNull
        @Size(min = 2, max = 120)
        String name,

        @NotNull
        LocalDate birthdate
) {
}
