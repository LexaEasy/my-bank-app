package ru.practicum.bank.cash.service;

import org.springframework.stereotype.Service;
import ru.practicum.bank.cash.client.AccountsBalanceOperationRequest;
import ru.practicum.bank.cash.client.AccountsClient;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.dto.CashOperationResponse;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.exception.InvalidAmountScaleException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CashService {

    private final AccountsClient accountsClient;

    public CashService(AccountsClient accountsClient) {
        this.accountsClient = accountsClient;
    }

    public CashOperationResponse deposit(String login, CashOperationRequest request) {
        validateAmount(request.amount());
        var balance = accountsClient.deposit(toAccountsRequest(login, request));

        return new CashOperationResponse(balance.balance(), balance.currency(), "Счёт пополнен");
    }

    public CashOperationResponse withdraw(String login, CashOperationRequest request) {
        validateAmount(request.amount());
        var balance = accountsClient.withdraw(toAccountsRequest(login, request));

        return new CashOperationResponse(balance.balance(), balance.currency(), "Деньги сняты со счёта");
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException();
        }
        if (amount.scale() > 2) {
            throw new InvalidAmountScaleException();
        }
    }

    private AccountsBalanceOperationRequest toAccountsRequest(String login, CashOperationRequest request) {
        return new AccountsBalanceOperationRequest(
                login,
                request.amount(),
                request.currency(),
                UUID.randomUUID().toString()
        );
    }
}
