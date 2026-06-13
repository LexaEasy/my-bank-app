package ru.practicum.bank.cash.service;

import org.junit.jupiter.api.Test;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.exception.AccountsClientNotConfiguredException;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.exception.InvalidAmountScaleException;
import ru.practicum.bank.cash.model.Currency;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashServiceTest {

    private final CashService cashService = new CashService();

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> cashService.deposit("ivan", request("0.00")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void shouldRejectAmountWithInvalidScale() {
        assertThatThrownBy(() -> cashService.deposit("ivan", request("100.001")))
                .isInstanceOf(InvalidAmountScaleException.class);
    }

    @Test
    void shouldFailUntilAccountsClientIsAdded() {
        assertThatThrownBy(() -> cashService.deposit("ivan", request("100.00")))
                .isInstanceOf(AccountsClientNotConfiguredException.class);
    }

    private CashOperationRequest request(String amount) {
        return new CashOperationRequest(new BigDecimal(amount), Currency.RUB);
    }
}
