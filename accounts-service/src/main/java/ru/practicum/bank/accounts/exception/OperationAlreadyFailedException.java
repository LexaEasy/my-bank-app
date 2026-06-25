package ru.practicum.bank.accounts.exception;

public class OperationAlreadyFailedException extends RuntimeException {

    public OperationAlreadyFailedException(String operationId) {
        super("Operation " + operationId + " has already failed");
    }
}
