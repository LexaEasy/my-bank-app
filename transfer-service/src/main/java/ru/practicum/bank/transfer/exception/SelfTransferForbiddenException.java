package ru.practicum.bank.transfer.exception;

public class SelfTransferForbiddenException extends RuntimeException {

    public SelfTransferForbiddenException() {
        super("Transfer to the same account is forbidden");
    }
}
