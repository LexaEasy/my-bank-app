package ru.practicum.bank.frontui.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AccountForm(
        @NotNull
        @Size(min = 2, max = 120)
        String name,

        @NotNull
        LocalDate birthdate
) {
}
