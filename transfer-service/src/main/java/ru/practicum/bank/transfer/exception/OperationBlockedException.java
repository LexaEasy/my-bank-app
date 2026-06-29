package ru.practicum.bank.transfer.exception;

public class OperationBlockedException extends RuntimeException {

    public OperationBlockedException(String message) {
        super(message);
    }
}
