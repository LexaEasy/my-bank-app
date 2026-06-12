package ru.practicum.bank.accounts.exception;

public class RecipientNotFoundException extends RuntimeException {

    public RecipientNotFoundException(String login) {
        super("Recipient not found: " + login);
    }
}
