package ru.practicum.bank.transfer.exception;

public class MissingPreferredUsernameException extends RuntimeException {

    public MissingPreferredUsernameException() {
        super("Preferred username is required");
    }
}
