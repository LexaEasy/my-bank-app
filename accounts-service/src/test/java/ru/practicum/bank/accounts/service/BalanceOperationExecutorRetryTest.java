package ru.practicum.bank.accounts.service;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import ru.practicum.bank.accounts.dto.TransferBalanceRequest;
import ru.practicum.bank.accounts.model.Account;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.accounts.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class BalanceOperationExecutorRetryTest {

    @Autowired
    private BalanceOperationExecutor executor;

    @MockitoBean
    private AccountRepository accountRepository;

    @Test
    void shouldRetryTransferAfterOptimisticLockException() {
        var firstSender = account("ivan", 1L, "1000.00");
        var firstRecipient = account("petr", 2L, "500.00");
        var secondSender = account("ivan", 1L, "1000.00");
        var secondRecipient = account("petr", 2L, "500.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(firstSender), Optional.of(secondSender));
        when(accountRepository.findByLogin("petr")).thenReturn(Optional.of(firstRecipient), Optional.of(secondRecipient));

        var saves = new AtomicInteger();
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            if (saves.incrementAndGet() == 1) {
                throw new OptimisticLockException("conflict");
            }
            return invocation.getArgument(0);
        });

        var response = executor.transfer(transferRequest("ivan", "petr", "150.00"));

        assertThat(response.senderBalance()).isEqualByComparingTo(new BigDecimal("850.00"));
        verify(accountRepository, times(2)).findByLogin("ivan");
        verify(accountRepository, times(2)).findByLogin("petr");
    }

    @Test
    void shouldRetryTransferAfterObjectOptimisticLockingFailureException() {
        var firstSender = account("ivan", 1L, "1000.00");
        var firstRecipient = account("petr", 2L, "500.00");
        var secondSender = account("ivan", 1L, "1000.00");
        var secondRecipient = account("petr", 2L, "500.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(firstSender), Optional.of(secondSender));
        when(accountRepository.findByLogin("petr")).thenReturn(Optional.of(firstRecipient), Optional.of(secondRecipient));

        var saves = new AtomicInteger();
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            if (saves.incrementAndGet() == 1) {
                throw new ObjectOptimisticLockingFailureException(Account.class, 1L);
            }
            return invocation.getArgument(0);
        });

        var response = executor.transfer(transferRequest("ivan", "petr", "150.00"));

        assertThat(response.senderBalance()).isEqualByComparingTo(new BigDecimal("850.00"));
        verify(accountRepository, times(2)).findByLogin("ivan");
        verify(accountRepository, times(2)).findByLogin("petr");
    }

    private TransferBalanceRequest transferRequest(String senderLogin, String recipientLogin, String amount) {
        return new TransferBalanceRequest(
                senderLogin,
                recipientLogin,
                new BigDecimal(amount),
                Currency.RUB,
                "operation-1"
        );
    }

    private Account account(String login, Long id, String balance) {
        var account = new Account(
                login,
                "Account " + login,
                LocalDate.of(1990, 1, 15),
                new BigDecimal(balance),
                Currency.RUB
        );
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
