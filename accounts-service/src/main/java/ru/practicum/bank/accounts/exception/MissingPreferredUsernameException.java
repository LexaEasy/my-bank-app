package ru.practicum.bank.accounts.exception;

public class MissingPreferredUsernameException extends RuntimeException {

    public MissingPreferredUsernameException() {
        super("JWT preferred_username claim is required");
    }
}
