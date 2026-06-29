package ru.practicum.bank.blocker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "bank.blocker")
public record BlockerProperties(
        BigDecimal maxAmount
) {
    public BlockerProperties {
        if (maxAmount == null) {
            maxAmount = new BigDecimal("100000.00");
        }
    }
}
