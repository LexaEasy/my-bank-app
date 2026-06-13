package ru.practicum.bank.cash.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.dto.CashOperationResponse;
import ru.practicum.bank.cash.exception.AccountsClientNotConfiguredException;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.exception.InvalidAmountScaleException;

import java.math.BigDecimal;

@Service
public class CashService {

    public CashOperationResponse deposit(String login, CashOperationRequest request) {
        validateAmount(request.amount());

        throw new AccountsClientNotConfiguredException();
    }

    public CashOperationResponse withdraw(String login, CashOperationRequest request) {
        validateAmount(request.amount());

        throw new AccountsClientNotConfiguredException();
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException();
        }
        if (amount.scale() > 2) {
            throw new InvalidAmountScaleException();
        }
    }
}
