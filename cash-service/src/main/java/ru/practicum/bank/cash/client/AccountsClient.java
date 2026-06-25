package ru.practicum.bank.cash.client;

public interface AccountsClient {

    AccountsBalanceResponse deposit(AccountsBalanceOperationRequest request);

    AccountsBalanceResponse withdraw(AccountsBalanceOperationRequest request);
}
