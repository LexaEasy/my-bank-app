package ru.practicum.bank.accounts.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String login) {
        super("Account not found: " + login);
    }
}
