package ru.practicum.bank.cash.client;

public class BlockerClientException extends RuntimeException {

    public BlockerClientException(String message) {
        super(message);
    }

    public BlockerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
