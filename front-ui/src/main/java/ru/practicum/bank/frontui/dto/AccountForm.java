package ru.practicum.bank.frontui.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record AccountForm(
        @NotNull
        @Size(min = 2, max = 120)
        String name,

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate birthdate
) {
}
