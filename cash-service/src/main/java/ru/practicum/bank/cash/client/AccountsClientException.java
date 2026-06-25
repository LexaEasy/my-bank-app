package ru.practicum.bank.cash.client;

public class AccountsClientException extends RuntimeException {

    public AccountsClientException(String message) {
        super(message);
    }

    public AccountsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
