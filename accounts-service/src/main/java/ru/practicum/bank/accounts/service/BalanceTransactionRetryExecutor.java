package ru.practicum.bank.accounts.service;

import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class BalanceTransactionRetryExecutor {

    @Retryable(
            retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
            maxAttemptsExpression = "${bank.balance.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${bank.balance.retry.backoff-ms:50}")
    )
    public <T> T execute(Supplier<T> transaction) {
        return transaction.get();
    }
}
