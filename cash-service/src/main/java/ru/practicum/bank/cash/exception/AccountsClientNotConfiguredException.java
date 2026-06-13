package ru.practicum.bank.cash.exception;

public class AccountsClientNotConfiguredException extends RuntimeException {

    public AccountsClientNotConfiguredException() {
        super("Accounts client is not configured yet");
    }
}
