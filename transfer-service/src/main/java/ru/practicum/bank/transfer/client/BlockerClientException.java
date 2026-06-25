package ru.practicum.bank.transfer.client;

public class BlockerClientException extends RuntimeException {

    public BlockerClientException(String message) {
        super(message);
    }

    public BlockerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
