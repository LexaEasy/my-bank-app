package ru.practicum.bank.exchange.exception;

public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException() {
        super("Amount must be positive and have scale no more than 2");
    }
}
