package ru.practicum.bank.accounts.exception;

public class InvalidAmountScaleException extends RuntimeException {

    public InvalidAmountScaleException() {
        super("Amount scale must not be greater than 2");
    }
}
