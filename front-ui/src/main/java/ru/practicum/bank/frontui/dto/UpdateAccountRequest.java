package ru.practicum.bank.frontui.dto;

import java.time.LocalDate;

public record UpdateAccountRequest(
        String name,
        LocalDate birthdate
) {
}
