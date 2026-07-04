package ru.practicum.bank.accounts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.model.ProcessedOperation;
import ru.practicum.bank.accounts.model.ProcessedOperationStatus;
import ru.practicum.bank.accounts.repository.AccountRepository;
import ru.practicum.bank.accounts.repository.ProcessedOperationRepository;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "bank.balance.retry.max-attempts=2",
        "bank.balance.retry.backoff-ms=0"
})
@DirtiesContext
class BalanceOperationExecutorRetryTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProcessedOperationRepository operationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldRetryWholeTransactionAfterConflictAtCommit() throws Exception {
        var operationId = "commit-conflict";
        var request = new BalanceOperationRequest(
                "ivan",
                new BigDecimal("25.00"),
                Currency.RUB,
                operationId
        );
        var initialBalance = accountRepository.findByLogin("ivan").orElseThrow().getBalance();
        var firstAttemptLoaded = new CountDownLatch(1);
        var concurrentCommitCompleted = new CountDownLatch(1);
        var attempts = new AtomicInteger();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = executor.submit(() -> idempotencyService.execute(
                    operationId,
                    "DEPOSIT",
                    request,
                    BalanceResponse.class,
                    () -> {
                        var account = accountRepository.findByLogin("ivan").orElseThrow();
                        if (attempts.incrementAndGet() == 1) {
                            firstAttemptLoaded.countDown();
                            await(concurrentCommitCompleted);
                        }
                        account.setBalance(account.getBalance().add(request.amount()));
                        accountRepository.save(account);
                        return new BalanceResponse(
                                account.getLogin(),
                                account.getBalance(),
                                account.getCurrency().name()
                        );
                    }
            ));

            assertThat(firstAttemptLoaded.await(5, TimeUnit.SECONDS)).isTrue();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                var account = accountRepository.findByLogin("ivan").orElseThrow();
                account.setBalance(account.getBalance().add(new BigDecimal("10.00")));
                accountRepository.saveAndFlush(account);
            });
            concurrentCommitCompleted.countDown();

            assertThat(result.get(10, TimeUnit.SECONDS).balance())
                    .isEqualByComparingTo(initialBalance.add(new BigDecimal("35.00")));
        }

        assertThat(attempts).hasValue(2);
        assertThat(accountRepository.findByLogin("ivan").orElseThrow().getBalance())
                .isEqualByComparingTo(initialBalance.add(new BigDecimal("35.00")));
        assertThat(operationRepository.findById(operationId))
                .get()
                .extracting(ProcessedOperation::getStatus)
                .isEqualTo(ProcessedOperationStatus.COMPLETED);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent transaction did not complete");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting concurrent transaction", exception);
        }
    }
}
