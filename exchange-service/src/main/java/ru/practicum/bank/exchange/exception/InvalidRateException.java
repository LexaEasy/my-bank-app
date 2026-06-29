package ru.practicum.bank.exchange.exception;

public class InvalidRateException extends RuntimeException {

    public InvalidRateException() {
        super("Rates must be positive and have scale no more than 4");
    }
}
