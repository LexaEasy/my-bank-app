package ru.practicum.bank.cash.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.practicum.bank.cash.client.AccountsBalanceOperationRequest;
import ru.practicum.bank.cash.client.AccountsBalanceResponse;
import ru.practicum.bank.cash.client.AccountsClient;
import ru.practicum.bank.cash.client.AccountsClientException;
import ru.practicum.bank.cash.client.BlockerClient;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.exception.InvalidAmountScaleException;
import ru.practicum.bank.cash.exception.OperationBlockedException;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.model.OperationType;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashServiceTest {

    private final AccountsClient accountsClient = mock(AccountsClient.class);
    private final BlockerClient blockerClient = mock(BlockerClient.class);
    private final NotificationEventPublisher notificationEventPublisher = mock(NotificationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-30T05:00:00Z"), ZoneOffset.UTC);
    private final CashService cashService =
            new CashService(accountsClient, blockerClient, notificationEventPublisher, clock);

    @Test
    void shouldDepositMoneyThroughAccountsService() {
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(accountsClient.deposit(any())).thenReturn(new AccountsBalanceResponse(
                "ivan",
                new BigDecimal("1250.00"),
                "RUB"
        ));

        var response = cashService.deposit("ivan", request("250.00"));

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("1250.00"));
        assertThat(response.currency()).isEqualTo("RUB");
        assertThat(response.message()).isEqualTo("Счёт пополнен");

        var captor = ArgumentCaptor.forClass(AccountsBalanceOperationRequest.class);
        verify(accountsClient).deposit(captor.capture());
        assertThat(captor.getValue().login()).isEqualTo("ivan");
        assertThat(captor.getValue().amount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(captor.getValue().currency()).isEqualTo(Currency.RUB);
        assertThat(captor.getValue().operationId()).isNotBlank();

        var blockerCaptor = ArgumentCaptor.forClass(OperationCheckRequest.class);
        verify(blockerClient).check(blockerCaptor.capture());
        assertThat(blockerCaptor.getValue().operationId()).isEqualTo(captor.getValue().operationId());
        assertThat(blockerCaptor.getValue().operationType()).isEqualTo(OperationType.DEPOSIT);
        assertThat(blockerCaptor.getValue().login()).isEqualTo("ivan");
        assertThat(blockerCaptor.getValue().amount()).isEqualByComparingTo(new BigDecimal("250.00"));

        var notificationCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventPublisher).publish(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().eventId()).isNotNull();
        assertThat(notificationCaptor.getValue().recipientLogin()).isEqualTo("ivan");
        assertThat(notificationCaptor.getValue().source()).isEqualTo(NotificationSource.CASH);
        assertThat(notificationCaptor.getValue().type()).isEqualTo(NotificationType.CASH_DEPOSITED);
        assertThat(notificationCaptor.getValue().message()).isEqualTo("Счёт пополнен на 250.00 RUB");
        assertThat(notificationCaptor.getValue().operationId().toString()).isEqualTo(captor.getValue().operationId());
        assertThat(notificationCaptor.getValue().occurredAt()).isEqualTo(clock.instant());
        assertThat(notificationCaptor.getValue().amount()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(notificationCaptor.getValue().currency()).isEqualTo(Currency.RUB);
    }

    @Test
    void shouldWithdrawMoneyThroughAccountsService() {
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(accountsClient.withdraw(any())).thenReturn(new AccountsBalanceResponse(
                "ivan",
                new BigDecimal("900.00"),
                "RUB"
        ));

        var response = cashService.withdraw("ivan", request("100.00"));

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(response.message()).isEqualTo("Деньги сняты со счёта");

        var accountsCaptor = ArgumentCaptor.forClass(AccountsBalanceOperationRequest.class);
        verify(accountsClient).withdraw(accountsCaptor.capture());
        var blockerCaptor = ArgumentCaptor.forClass(OperationCheckRequest.class);
        verify(blockerClient).check(blockerCaptor.capture());
        assertThat(blockerCaptor.getValue().operationId()).isEqualTo(accountsCaptor.getValue().operationId());
        assertThat(blockerCaptor.getValue().operationType()).isEqualTo(OperationType.WITHDRAW);

        var notificationCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventPublisher).publish(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().eventId()).isNotNull();
        assertThat(notificationCaptor.getValue().recipientLogin()).isEqualTo("ivan");
        assertThat(notificationCaptor.getValue().source()).isEqualTo(NotificationSource.CASH);
        assertThat(notificationCaptor.getValue().type()).isEqualTo(NotificationType.CASH_WITHDRAWN);
        assertThat(notificationCaptor.getValue().message()).isEqualTo("Со счёта снято 100.00 RUB");
        assertThat(notificationCaptor.getValue().operationId().toString())
                .isEqualTo(accountsCaptor.getValue().operationId());
        assertThat(notificationCaptor.getValue().occurredAt()).isEqualTo(clock.instant());
        assertThat(notificationCaptor.getValue().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(notificationCaptor.getValue().currency()).isEqualTo(Currency.RUB);
    }

    @Test
    void shouldNotPublishEventWhenAccountsDepositFails() {
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(accountsClient.deposit(any())).thenThrow(new AccountsClientException("Accounts service request failed"));

        assertThatThrownBy(() -> cashService.deposit("ivan", request("250.00")))
                .isInstanceOf(AccountsClientException.class);
        verify(notificationEventPublisher, never()).publish(any());
    }

    @Test
    void shouldNotPublishEventWhenAccountsWithdrawFails() {
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(accountsClient.withdraw(any()))
                .thenThrow(new AccountsClientException("Accounts service request failed"));

        assertThatThrownBy(() -> cashService.withdraw("ivan", request("100.00")))
                .isInstanceOf(AccountsClientException.class);
        verify(notificationEventPublisher, never()).publish(any());
    }

    @Test
    void shouldNotChangeBalanceWhenDepositWasBlocked() {
        when(blockerClient.check(any()))
                .thenReturn(new OperationCheckResponse(false, "Operation amount exceeds blocker limit"));

        assertThatThrownBy(() -> cashService.deposit("ivan", request("100000.01")))
                .isInstanceOf(OperationBlockedException.class)
                .hasMessage("Operation amount exceeds blocker limit");

        verify(accountsClient, never()).deposit(any());
        verify(accountsClient, never()).withdraw(any());
        verify(notificationEventPublisher, never()).publish(any());
    }

    @Test
    void shouldCheckBlockerBeforeWithdrawFromAccounts() {
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(accountsClient.withdraw(any())).thenReturn(new AccountsBalanceResponse(
                "ivan",
                new BigDecimal("900.00"),
                "RUB"
        ));

        cashService.withdraw("ivan", request("100.00"));

        var ordered = inOrder(blockerClient, accountsClient);
        ordered.verify(blockerClient).check(any());
        ordered.verify(accountsClient).withdraw(any());
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> cashService.deposit("ivan", request("0.00")))
                .isInstanceOf(InvalidAmountException.class);
        verify(blockerClient, never()).check(any());
        verify(accountsClient, never()).deposit(any());
        verify(notificationEventPublisher, never()).publish(any());
    }

    @Test
    void shouldRejectAmountWithInvalidScale() {
        assertThatThrownBy(() -> cashService.deposit("ivan", request("100.001")))
                .isInstanceOf(InvalidAmountScaleException.class);
        verify(blockerClient, never()).check(any());
        verify(accountsClient, never()).deposit(any());
        verify(notificationEventPublisher, never()).publish(any());
    }

    private CashOperationRequest request(String amount) {
        return new CashOperationRequest(new BigDecimal(amount), Currency.RUB);
    }
}
