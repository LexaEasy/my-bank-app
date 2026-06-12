package ru.practicum.bank.accounts.exception;

public class SelfTransferForbiddenException extends RuntimeException {

    public SelfTransferForbiddenException() {
        super("Transfer to the same account is forbidden");
    }
}
