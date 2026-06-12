package ru.practicum.bank.accounts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.TransferBalanceRequest;
import ru.practicum.bank.accounts.exception.InsufficientFundsException;
import ru.practicum.bank.accounts.exception.InvalidAmountException;
import ru.practicum.bank.accounts.exception.InvalidAmountScaleException;
import ru.practicum.bank.accounts.exception.RecipientNotFoundException;
import ru.practicum.bank.accounts.exception.SelfTransferForbiddenException;
import ru.practicum.bank.accounts.model.Account;
import ru.practicum.bank.accounts.model.Currency;
import ru.practicum.bank.accounts.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalanceServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(accountRepository);
    }

    @Test
    void shouldDepositMoney() {
        var account = account("ivan", "1000.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        var response = balanceService.deposit(operationRequest("ivan", "250.00"));

        assertThat(response.login()).isEqualTo("ivan");
        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("1250.00"));
        assertThat(response.currency()).isEqualTo("RUB");
        verify(accountRepository).save(account);
    }

    @Test
    void shouldWithdrawMoney() {
        var account = account("ivan", "1000.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        var response = balanceService.withdraw(operationRequest("ivan", "100.00"));

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("900.00"));
        verify(accountRepository).save(account);
    }

    @Test
    void shouldTransferMoney() {
        var sender = account("ivan", "1000.00");
        var recipient = account("petr", "500.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(sender));
        when(accountRepository.findByLogin("petr")).thenReturn(Optional.of(recipient));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = balanceService.transfer(transferRequest("ivan", "petr", "150.00"));

        assertThat(response.senderLogin()).isEqualTo("ivan");
        assertThat(response.recipientLogin()).isEqualTo("petr");
        assertThat(response.senderBalance()).isEqualByComparingTo(new BigDecimal("850.00"));
        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("850.00"));
        assertThat(recipient.getBalance()).isEqualByComparingTo(new BigDecimal("650.00"));
        verify(accountRepository).save(sender);
        verify(accountRepository).save(recipient);
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> balanceService.deposit(operationRequest("ivan", "0.00")))
                .isInstanceOf(InvalidAmountException.class);
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    void shouldRejectAmountWithInvalidScale() {
        assertThatThrownBy(() -> balanceService.deposit(operationRequest("ivan", "100.001")))
                .isInstanceOf(InvalidAmountScaleException.class);
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    void shouldRejectWithdrawWhenBalanceIsInsufficient() {
        var account = account("ivan", "100.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> balanceService.withdraw(operationRequest("ivan", "150.00")))
                .isInstanceOf(InsufficientFundsException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldRejectSelfTransfer() {
        assertThatThrownBy(() -> balanceService.transfer(transferRequest("ivan", "ivan", "10.00")))
                .isInstanceOf(SelfTransferForbiddenException.class);
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    void shouldRejectTransferWhenRecipientNotFound() {
        var sender = account("ivan", "1000.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(sender));
        when(accountRepository.findByLogin("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.transfer(transferRequest("ivan", "unknown", "10.00")))
                .isInstanceOf(RecipientNotFoundException.class);
        verify(accountRepository, never()).save(any());
    }

    private BalanceOperationRequest operationRequest(String login, String amount) {
        return new BalanceOperationRequest(login, new BigDecimal(amount), Currency.RUB, "operation-1");
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

    private Account account(String login, String balance) {
        return new Account(
                login,
                "Account " + login,
                LocalDate.of(1990, 1, 15),
                new BigDecimal(balance),
                Currency.RUB
        );
    }
}
