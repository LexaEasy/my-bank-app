package ru.practicum.bank.cash.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.practicum.bank.cash.client.AccountsBalanceOperationRequest;
import ru.practicum.bank.cash.client.AccountsBalanceResponse;
import ru.practicum.bank.cash.client.AccountsClient;
import ru.practicum.bank.cash.client.AccountsClientException;
import ru.practicum.bank.cash.client.BlockerClient;
import ru.practicum.bank.cash.client.NotificationRequest;
import ru.practicum.bank.cash.client.NotificationsClient;
import ru.practicum.bank.cash.dto.CashOperationRequest;
import ru.practicum.bank.cash.exception.InvalidAmountException;
import ru.practicum.bank.cash.exception.InvalidAmountScaleException;
import ru.practicum.bank.cash.exception.OperationBlockedException;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.model.OperationType;

import java.math.BigDecimal;

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
    private final NotificationsClient notificationsClient = mock(NotificationsClient.class);
    private final CashService cashService = new CashService(accountsClient, blockerClient, notificationsClient);

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

        var notificationCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationsClient).notify(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().recipientLogin()).isEqualTo("ivan");
        assertThat(notificationCaptor.getValue().type()).isEqualTo("CASH_DEPOSIT");
        assertThat(notificationCaptor.getValue().message()).isEqualTo("Счёт пополнен на 250.00 RUB");
        assertThat(notificationCaptor.getValue().operationId()).isEqualTo(captor.getValue().operationId());
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

        var notificationCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationsClient).notify(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().recipientLogin()).isEqualTo("ivan");
        assertThat(notificationCaptor.getValue().type()).isEqualTo("CASH_WITHDRAW");
        assertThat(notificationCaptor.getValue().message()).isEqualTo("Со счёта снято 100.00 RUB");
        assertThat(notificationCaptor.getValue().operationId()).isEqualTo(accountsCaptor.getValue().operationId());
    }

    @Test
    void shouldNotNotifyWhenAccountsDepositFails() {
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(accountsClient.deposit(any())).thenThrow(new AccountsClientException("Accounts service request failed"));

        assertThatThrownBy(() -> cashService.deposit("ivan", request("250.00")))
                .isInstanceOf(AccountsClientException.class);
        verify(notificationsClient, never()).notify(any());
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
        verify(notificationsClient, never()).notify(any());
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
        verify(notificationsClient, never()).notify(any());
    }

    @Test
    void shouldRejectAmountWithInvalidScale() {
        assertThatThrownBy(() -> cashService.deposit("ivan", request("100.001")))
                .isInstanceOf(InvalidAmountScaleException.class);
        verify(blockerClient, never()).check(any());
        verify(accountsClient, never()).deposit(any());
        verify(notificationsClient, never()).notify(any());
    }

    private CashOperationRequest request(String amount) {
        return new CashOperationRequest(new BigDecimal(amount), Currency.RUB);
    }
}
