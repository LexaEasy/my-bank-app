package ru.practicum.bank.accounts.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String operationId) {
        super("Operation id " + operationId + " was already used with another request");
    }
}
