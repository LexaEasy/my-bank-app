package ru.practicum.bank.accounts.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.exception.IdempotencyConflictException;
import ru.practicum.bank.accounts.exception.OperationInProgressException;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.accounts.model.ProcessedOperation;
import ru.practicum.bank.accounts.model.ProcessedOperationStatus;
import ru.practicum.bank.accounts.repository.AccountRepository;
import ru.practicum.bank.accounts.repository.ProcessedOperationRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IdempotencyServiceTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private ProcessedOperationRepository operationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private Clock clock;

    @Test
    void shouldReturnStoredResponseForRepeatedOperationId() {
        String operationId = "repeat-operation";
        var calls = new AtomicInteger();

        var firstResponse = idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request(operationId, "ivan", "100.00"),
                BalanceResponse.class,
                () -> {
                    calls.incrementAndGet();
                    return new BalanceResponse("ivan", new BigDecimal("1100.00"), "RUB");
                }
        );
        var repeatedResponse = idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request(operationId, "ivan", "100.00"),
                BalanceResponse.class,
                () -> {
                    calls.incrementAndGet();
                    return new BalanceResponse("ivan", new BigDecimal("1200.00"), "RUB");
                }
        );

        assertThat(firstResponse.balance()).isEqualByComparingTo(new BigDecimal("1100.00"));
        assertThat(repeatedResponse.balance()).isEqualByComparingTo(new BigDecimal("1100.00"));
        assertThat(calls).hasValue(1);
        assertThat(operationRepository.findById(operationId))
                .get()
                .extracting(ProcessedOperation::getStatus)
                .isEqualTo(ProcessedOperationStatus.COMPLETED);
    }

    @Test
    void shouldRejectSameOperationIdWithDifferentRequestHash() {
        String operationId = "conflict-operation";
        idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request(operationId, "ivan", "100.00"),
                BalanceResponse.class,
                () -> new BalanceResponse("ivan", new BigDecimal("1100.00"), "RUB")
        );

        assertThatThrownBy(() -> idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request(operationId, "ivan", "200.00"),
                BalanceResponse.class,
                () -> new BalanceResponse("ivan", new BigDecimal("1200.00"), "RUB")
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void shouldRejectRepeatedOperationWhileProcessing() {
        String operationId = "processing-operation";
        var request = request(operationId, "ivan", "100.00");
        operationRepository.saveAndFlush(new ProcessedOperation(
                operationId,
                "DEPOSIT",
                idempotencyService.hashRequest("DEPOSIT", request),
                LocalDateTime.now(clock)
        ));

        assertThatThrownBy(() -> idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request,
                BalanceResponse.class,
                () -> new BalanceResponse("ivan", new BigDecimal("1100.00"), "RUB")
        )).isInstanceOf(OperationInProgressException.class);
    }

    @Test
    void shouldReleaseOperationWhenBusinessOperationFails() {
        String operationId = "failed-operation";

        assertThatThrownBy(() -> idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request(operationId, "ivan", "100.00"),
                BalanceResponse.class,
                () -> {
                    throw new IllegalStateException("failure");
                }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(operationRepository.findById(operationId)).isEmpty();

        var response = idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request(operationId, "ivan", "100.00"),
                BalanceResponse.class,
                () -> new BalanceResponse("ivan", new BigDecimal("1100.00"), "RUB")
        );

        assertThat(response.balance()).isEqualByComparingTo("1100.00");
    }

    @Test
    void shouldRejectSameKeyForDifferentOperationType() {
        String operationId = "operation-type-conflict";
        var request = request(operationId, "ivan", "100.00");
        idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request,
                BalanceResponse.class,
                () -> new BalanceResponse("ivan", new BigDecimal("1100.00"), "RUB")
        );

        assertThatThrownBy(() -> idempotencyService.execute(
                operationId,
                "WITHDRAW",
                request,
                BalanceResponse.class,
                () -> new BalanceResponse("ivan", new BigDecimal("900.00"), "RUB")
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void shouldTreatEquivalentDecimalPayloadAsSameRequest() {
        String operationId = "normalized-amount";
        idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request(operationId, "ivan", "100.0"),
                BalanceResponse.class,
                () -> new BalanceResponse("ivan", new BigDecimal("1100.00"), "RUB")
        );

        var repeated = idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request(operationId, "ivan", "100.00"),
                BalanceResponse.class,
                () -> {
                    throw new AssertionError("Business operation must not be repeated");
                }
        );

        assertThat(repeated.balance()).isEqualByComparingTo("1100.00");
    }

    @Test
    void shouldRecoverStaleProcessingOperation() {
        String operationId = "stale-processing";
        var request = request(operationId, "ivan", "100.00");
        operationRepository.saveAndFlush(new ProcessedOperation(
                operationId,
                "DEPOSIT",
                idempotencyService.hashRequest("DEPOSIT", request),
                LocalDateTime.now(clock).minusMinutes(6)
        ));

        var response = idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request,
                BalanceResponse.class,
                () -> new BalanceResponse("ivan", new BigDecimal("1100.00"), "RUB")
        );

        assertThat(response.balance()).isEqualByComparingTo("1100.00");
        assertThat(operationRepository.findById(operationId))
                .get()
                .extracting(ProcessedOperation::getStatus)
                .isEqualTo(ProcessedOperationStatus.COMPLETED);
    }

    @Test
    void shouldChangeBalanceOnlyOnceForRepeatedOperationId() {
        String operationId = "repeat-balance-operation";
        BigDecimal initialBalance = accountRepository.findByLogin("ivan").orElseThrow().getBalance();
        var request = request(operationId, "ivan", "25.00");

        idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request,
                BalanceResponse.class,
                () -> deposit("ivan", "25.00")
        );
        idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request,
                BalanceResponse.class,
                () -> deposit("ivan", "25.00")
        );

        assertThat(accountRepository.findByLogin("ivan").orElseThrow().getBalance())
                .isEqualByComparingTo(initialBalance.add(new BigDecimal("25.00")));
    }

    @Test
    void shouldRollbackBalanceAndReleaseOperationWhenTransactionFails() {
        String operationId = "balance-rollback";
        BigDecimal initialBalance = accountRepository.findByLogin("ivan").orElseThrow().getBalance();

        assertThatThrownBy(() -> idempotencyService.execute(
                operationId,
                "DEPOSIT",
                request(operationId, "ivan", "25.00"),
                BalanceResponse.class,
                () -> {
                    var account = accountRepository.findByLogin("ivan").orElseThrow();
                    account.setBalance(account.getBalance().add(new BigDecimal("25.00")));
                    accountRepository.save(account);
                    throw new IllegalStateException("failure after balance update");
                }
        )).isInstanceOf(IllegalStateException.class);

        assertThat(accountRepository.findByLogin("ivan").orElseThrow().getBalance())
                .isEqualByComparingTo(initialBalance);
        assertThat(operationRepository.findById(operationId)).isEmpty();
    }

    private BalanceOperationRequest request(String operationId, String login, String amount) {
        return new BalanceOperationRequest(login, new BigDecimal(amount), Currency.RUB, operationId);
    }

    private BalanceResponse deposit(String login, String amount) {
        var account = accountRepository.findByLogin(login).orElseThrow();
        account.setBalance(account.getBalance().add(new BigDecimal(amount)));
        var saved = accountRepository.save(account);
        return new BalanceResponse(saved.getLogin(), saved.getBalance(), saved.getCurrency().name());
    }

}
