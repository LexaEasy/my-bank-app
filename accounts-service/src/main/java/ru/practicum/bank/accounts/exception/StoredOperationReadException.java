package ru.practicum.bank.accounts.exception;

public class StoredOperationReadException extends RuntimeException {

    public StoredOperationReadException(String operationId, Throwable cause) {
        super("Cannot read stored response for operation " + operationId, cause);
    }
}
