package ru.practicum.bank.transfer.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.practicum.bank.transfer.client.AccountsClientException;
import ru.practicum.bank.transfer.client.NotificationRequest;
import ru.practicum.bank.transfer.client.NotificationsClient;
import ru.practicum.bank.transfer.dto.TransferRequest;
import ru.practicum.bank.transfer.exception.InvalidAmountException;
import ru.practicum.bank.transfer.exception.InvalidAmountScaleException;
import ru.practicum.bank.transfer.exception.SelfTransferForbiddenException;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceTest {

    private final TransferExecutor transferExecutor = mock(TransferExecutor.class);
    private final NotificationsClient notificationsClient = mock(NotificationsClient.class);
    private final TransferService transferService = new TransferService(transferExecutor, notificationsClient);

    @Test
    void shouldTransferMoney() {
        when(transferExecutor.execute(any())).thenReturn(new TransferResult(
                "ivan",
                "olga",
                new BigDecimal("800.00"),
                "RUB"
        ));

        var response = transferService.transfer("ivan", request("olga", "200.00"));

        assertThat(response.senderLogin()).isEqualTo("ivan");
        assertThat(response.recipientLogin()).isEqualTo("olga");
        assertThat(response.senderBalance()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(response.currency()).isEqualTo("RUB");
        assertThat(response.message()).isEqualTo("Transfer completed");

        var captor = ArgumentCaptor.forClass(TransferOperation.class);
        verify(transferExecutor).execute(captor.capture());
        assertThat(captor.getValue().senderLogin()).isEqualTo("ivan");
        assertThat(captor.getValue().recipientLogin()).isEqualTo("olga");
        assertThat(captor.getValue().amount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(captor.getValue().currency()).isEqualTo(Currency.RUB);
        assertThat(captor.getValue().operationId()).isNotBlank();

        var notificationCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationsClient).notify(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().recipientLogin()).isEqualTo("ivan");
        assertThat(notificationCaptor.getValue().type()).isEqualTo("TRANSFER_COMPLETED");
        assertThat(notificationCaptor.getValue().message()).isEqualTo("Transfer completed to olga: 200.00 RUB");
        assertThat(notificationCaptor.getValue().operationId()).isEqualTo(captor.getValue().operationId());
    }

    @Test
    void shouldNotNotifyWhenAccountsTransferFails() {
        when(transferExecutor.execute(any())).thenThrow(new AccountsClientException("Accounts service request failed"));

        assertThatThrownBy(() -> transferService.transfer("ivan", request("olga", "200.00")))
                .isInstanceOf(AccountsClientException.class);
        verify(notificationsClient, never()).notify(any());
    }

    @Test
    void shouldRejectSelfTransfer() {
        assertThatThrownBy(() -> transferService.transfer("ivan", request("ivan", "200.00")))
                .isInstanceOf(SelfTransferForbiddenException.class);
        verify(transferExecutor, never()).execute(any());
        verify(notificationsClient, never()).notify(any());
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> transferService.transfer("ivan", request("olga", "0.00")))
                .isInstanceOf(InvalidAmountException.class);
        verify(transferExecutor, never()).execute(any());
        verify(notificationsClient, never()).notify(any());
    }

    @Test
    void shouldRejectAmountWithInvalidScale() {
        assertThatThrownBy(() -> transferService.transfer("ivan", request("olga", "100.001")))
                .isInstanceOf(InvalidAmountScaleException.class);
        verify(transferExecutor, never()).execute(any());
        verify(notificationsClient, never()).notify(any());
    }

    private TransferRequest request(String recipientLogin, String amount) {
        return new TransferRequest(recipientLogin, new BigDecimal(amount), Currency.RUB);
    }
}
