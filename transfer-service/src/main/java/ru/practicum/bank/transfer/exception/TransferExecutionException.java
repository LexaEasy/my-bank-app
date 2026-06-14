package ru.practicum.bank.transfer.exception;

public class TransferExecutionException extends RuntimeException {

    public TransferExecutionException(String message) {
        super(message);
    }
}
