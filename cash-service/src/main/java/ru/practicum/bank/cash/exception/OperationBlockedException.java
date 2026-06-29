package ru.practicum.bank.cash.exception;

public class OperationBlockedException extends RuntimeException {

    public OperationBlockedException(String message) {
        super(message);
    }
}
