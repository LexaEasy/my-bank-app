package ru.practicum.bank.accounts.exception;

public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException() {
        super("Amount must be greater than zero");
    }
}
