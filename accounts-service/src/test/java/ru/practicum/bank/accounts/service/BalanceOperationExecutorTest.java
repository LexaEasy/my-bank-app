package ru.practicum.bank.accounts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.TransferBalanceRequest;
import ru.practicum.bank.accounts.exception.CurrencyMismatchException;
import ru.practicum.bank.accounts.exception.InsufficientFundsException;
import ru.practicum.bank.accounts.exception.InvalidAmountException;
import ru.practicum.bank.accounts.exception.InvalidAmountScaleException;
import ru.practicum.bank.accounts.exception.RecipientNotFoundException;
import ru.practicum.bank.accounts.exception.SelfTransferForbiddenException;
import ru.practicum.bank.accounts.model.Account;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.accounts.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class BalanceOperationExecutorTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);

    private BalanceOperationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new BalanceOperationExecutor(accountRepository);
    }

    @Test
    void shouldDepositMoney() {
        var account = account("ivan", 1L, "1000.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        var response = executor.deposit(operationRequest("ivan", "250.00"));

        assertThat(response.login()).isEqualTo("ivan");
        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("1250.00"));
        assertThat(response.currency()).isEqualTo("RUB");
        verify(accountRepository).save(account);
    }

    @Test
    void shouldWithdrawMoney() {
        var account = account("ivan", 1L, "1000.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        var response = executor.withdraw(operationRequest("ivan", "100.00"));

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("900.00"));
        verify(accountRepository).save(account);
    }

    @Test
    void shouldTransferMoney() {
        var sender = account("ivan", 1L, "1000.00");
        var recipient = account("petr", 2L, "500.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(sender));
        when(accountRepository.findByLogin("petr")).thenReturn(Optional.of(recipient));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = executor.transfer(transferRequest("ivan", "petr", "150.00"));

        assertThat(response.senderLogin()).isEqualTo("ivan");
        assertThat(response.recipientLogin()).isEqualTo("petr");
        assertThat(response.senderBalance()).isEqualByComparingTo(new BigDecimal("850.00"));
        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("850.00"));
        assertThat(recipient.getBalance()).isEqualByComparingTo(new BigDecimal("650.00"));
        verify(accountRepository).save(sender);
        verify(accountRepository).save(recipient);
    }

    @Test
    void shouldTransferConvertedRecipientAmount() {
        var sender = account("ivan", 1L, "1000.00", Currency.USD);
        var recipient = account("petr", 2L, "500.00", Currency.CNY);
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(sender));
        when(accountRepository.findByLogin("petr")).thenReturn(Optional.of(recipient));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = executor.transfer(new TransferBalanceRequest(
                "ivan",
                "petr",
                new BigDecimal("100.00"),
                Currency.USD,
                new BigDecimal("741.94"),
                Currency.CNY,
                "operation-1"
        ));

        assertThat(response.senderBalance()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(recipient.getBalance()).isEqualByComparingTo(new BigDecimal("1241.94"));
        assertThat(response.currency()).isEqualTo("USD");
    }

    @Test
    void shouldRejectDepositWhenCurrencyDoesNotMatchAccount() {
        var account = account("ivan", 1L, "1000.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> executor.deposit(operationRequest("ivan", "250.00", Currency.USD)))
                .isInstanceOf(CurrencyMismatchException.class);

        verify(accountRepository, never()).save(any());
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void shouldRejectWithdrawWhenCurrencyDoesNotMatchAccount() {
        var account = account("ivan", 1L, "1000.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> executor.withdraw(operationRequest("ivan", "250.00", Currency.USD)))
                .isInstanceOf(CurrencyMismatchException.class);

        verify(accountRepository, never()).save(any());
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void shouldRejectTransferWhenSenderCurrencyDoesNotMatchAccount() {
        var sender = account("ivan", 1L, "1000.00", Currency.RUB);
        var recipient = account("petr", 2L, "500.00", Currency.CNY);
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(sender));
        when(accountRepository.findByLogin("petr")).thenReturn(Optional.of(recipient));

        assertThatThrownBy(() -> executor.transfer(new TransferBalanceRequest(
                "ivan",
                "petr",
                new BigDecimal("100.00"),
                Currency.USD,
                new BigDecimal("741.94"),
                Currency.CNY,
                "operation-1"
        ))).isInstanceOf(CurrencyMismatchException.class);

        verify(accountRepository, never()).save(any());
        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(recipient.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void shouldRejectTransferWhenRecipientCurrencyDoesNotMatchAccount() {
        var sender = account("ivan", 1L, "1000.00", Currency.USD);
        var recipient = account("petr", 2L, "500.00", Currency.RUB);
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(sender));
        when(accountRepository.findByLogin("petr")).thenReturn(Optional.of(recipient));

        assertThatThrownBy(() -> executor.transfer(new TransferBalanceRequest(
                "ivan",
                "petr",
                new BigDecimal("100.00"),
                Currency.USD,
                new BigDecimal("741.94"),
                Currency.CNY,
                "operation-1"
        ))).isInstanceOf(CurrencyMismatchException.class);

        verify(accountRepository, never()).save(any());
        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(recipient.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void shouldSaveTransferAccountsInIdOrder() {
        var sender = account("ivan", 2L, "1000.00");
        var recipient = account("petr", 1L, "500.00");
        var savedLogins = new ArrayList<String>();
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(sender));
        when(accountRepository.findByLogin("petr")).thenReturn(Optional.of(recipient));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            savedLogins.add(account.getLogin());
            return account;
        });

        executor.transfer(transferRequest("ivan", "petr", "150.00"));

        assertThat(savedLogins).containsExactly("petr", "ivan");
    }

    @Test
    void shouldRollbackTransferWhenRecipientNotFound() {
        var sender = account("ivan", 1L, "1000.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(sender));
        when(accountRepository.findByLogin("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.transfer(transferRequest("ivan", "unknown", "10.00")))
                .isInstanceOf(RecipientNotFoundException.class);
        verify(accountRepository, never()).save(any());
        assertThat(sender.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> executor.deposit(operationRequest("ivan", "0.00")))
                .isInstanceOf(InvalidAmountException.class);
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    void shouldRejectAmountWithInvalidScale() {
        assertThatThrownBy(() -> executor.deposit(operationRequest("ivan", "100.001")))
                .isInstanceOf(InvalidAmountScaleException.class);
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    void shouldRejectWithdrawWhenBalanceIsInsufficient(CapturedOutput output) {
        var account = account("ivan", 1L, "100.00");
        when(accountRepository.findByLogin("ivan")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> executor.withdraw(operationRequest("ivan", "150.00")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(output)
                .contains("Balance operation rejected")
                .contains("operationId=operation-1")
                .contains("operationType=WITHDRAW")
                .contains("currency=RUB")
                .contains("errorCode=INSUFFICIENT_FUNDS")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("password")
                .doesNotContain("client_secret");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldRejectSelfTransfer() {
        assertThatThrownBy(() -> executor.transfer(transferRequest("ivan", "ivan", "10.00")))
                .isInstanceOf(SelfTransferForbiddenException.class);
        verify(accountRepository, never()).findByLogin(any());
    }

    private BalanceOperationRequest operationRequest(String login, String amount) {
        return operationRequest(login, amount, Currency.RUB);
    }

    private BalanceOperationRequest operationRequest(String login, String amount, Currency currency) {
        return new BalanceOperationRequest(login, new BigDecimal(amount), currency, "operation-1");
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
        return account(login, id, balance, Currency.RUB);
    }

    private Account account(String login, Long id, String balance, Currency currency) {
        var account = new Account(
                login,
                "Account " + login,
                LocalDate.of(1990, 1, 15),
                new BigDecimal(balance),
                currency
        );
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
