package ru.practicum.bank.accounts.exception;

public class OperationInProgressException extends RuntimeException {

    public OperationInProgressException(String operationId) {
        super("Operation " + operationId + " is already processing");
    }
}
