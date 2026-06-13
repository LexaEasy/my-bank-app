package ru.practicum.bank.cash.exception;

public class InvalidAmountScaleException extends RuntimeException {

    public InvalidAmountScaleException() {
        super("Amount scale must not be greater than 2");
    }
}
