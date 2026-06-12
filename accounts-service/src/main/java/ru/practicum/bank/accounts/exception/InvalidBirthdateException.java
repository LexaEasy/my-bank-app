package ru.practicum.bank.accounts.exception;

public class InvalidBirthdateException extends RuntimeException {

    public InvalidBirthdateException() {
        super("Account owner must be at least 18 years old");
    }
}
