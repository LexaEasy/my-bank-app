package ru.practicum.bank.transfer.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.dto.blocker.OperationCheckResponse;
import ru.practicum.bank.common.dto.exchange.ConversionResponse;
import ru.practicum.bank.common.model.OperationType;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationEventPublisher;
import ru.practicum.bank.common.notification.NotificationSource;
import ru.practicum.bank.common.notification.NotificationType;
import ru.practicum.bank.transfer.client.AccountsClientException;
import ru.practicum.bank.transfer.client.BlockerClient;
import ru.practicum.bank.transfer.client.ExchangeClient;
import ru.practicum.bank.transfer.dto.TransferRequest;
import ru.practicum.bank.transfer.exception.InvalidAmountException;
import ru.practicum.bank.transfer.exception.InvalidAmountScaleException;
import ru.practicum.bank.transfer.exception.OperationBlockedException;
import ru.practicum.bank.transfer.exception.SelfTransferForbiddenException;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceTest {

    private static final UUID OPERATION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final TransferExecutor transferExecutor = mock(TransferExecutor.class);
    private final BlockerClient blockerClient = mock(BlockerClient.class);
    private final ExchangeClient exchangeClient = mock(ExchangeClient.class);
    private final NotificationEventPublisher notificationEventPublisher = mock(NotificationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-30T05:00:00Z"), ZoneOffset.UTC);
    private final TransferService transferService = new TransferService(
            transferExecutor,
            blockerClient,
            exchangeClient,
            notificationEventPublisher,
            clock
    );

    @Test
    void shouldTransferMoney() {
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(transferExecutor.execute(any())).thenReturn(new TransferResult(
                "ivan",
                "olga",
                new BigDecimal("800.00"),
                "RUB"
        ));

        var response = transferService.transfer("ivan", request("olga", "200.00"), OPERATION_ID);

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
        assertThat(captor.getValue().recipientAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(captor.getValue().recipientCurrency()).isEqualTo(Currency.RUB);
        assertThat(captor.getValue().operationId()).isEqualTo(OPERATION_ID.toString());

        var blockerCaptor = ArgumentCaptor.forClass(OperationCheckRequest.class);
        verify(blockerClient).check(blockerCaptor.capture());
        assertThat(blockerCaptor.getValue().operationId()).isEqualTo(captor.getValue().operationId());
        assertThat(blockerCaptor.getValue().operationType()).isEqualTo(OperationType.TRANSFER);
        assertThat(blockerCaptor.getValue().sender()).isEqualTo("ivan");
        assertThat(blockerCaptor.getValue().recipient()).isEqualTo("olga");
        verify(exchangeClient, never()).convert(any(), any(), any());

        var notificationCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventPublisher, times(2)).publish(notificationCaptor.capture());
        var notifications = notificationCaptor.getAllValues();
        var outgoing = notifications.get(0);
        var incoming = notifications.get(1);

        assertThat(outgoing.eventId()).isNotEqualTo(incoming.eventId());
        assertThat(outgoing.operationId()).isEqualTo(incoming.operationId());
        assertThat(outgoing.operationId().toString()).isEqualTo(captor.getValue().operationId());
        assertThat(outgoing.source()).isEqualTo(NotificationSource.TRANSFER);
        assertThat(outgoing.type()).isEqualTo(NotificationType.TRANSFER_OUTGOING);
        assertThat(outgoing.recipientLogin()).isEqualTo("ivan");
        assertThat(outgoing.message()).isEqualTo("Перевод пользователю olga: 200.00 RUB");
        assertThat(outgoing.occurredAt()).isEqualTo(clock.instant());
        assertThat(outgoing.amount()).isEqualByComparingTo("200.00");
        assertThat(outgoing.currency()).isEqualTo(Currency.RUB);

        assertThat(incoming.source()).isEqualTo(NotificationSource.TRANSFER);
        assertThat(incoming.type()).isEqualTo(NotificationType.TRANSFER_INCOMING);
        assertThat(incoming.recipientLogin()).isEqualTo("olga");
        assertThat(incoming.message()).isEqualTo("Получен перевод от ivan: 200.00 RUB");
        assertThat(incoming.occurredAt()).isEqualTo(clock.instant());
        assertThat(incoming.amount()).isEqualByComparingTo("200.00");
        assertThat(incoming.currency()).isEqualTo(Currency.RUB);
    }

    @Test
    void shouldConvertTransferForDifferentTargetCurrency() {
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(exchangeClient.convert(Currency.USD, Currency.CNY, new BigDecimal("100.00")))
                .thenReturn(new ConversionResponse(
                        Currency.USD,
                        Currency.CNY,
                        new BigDecimal("100.00"),
                        new BigDecimal("741.94"),
                        new BigDecimal("7.419355"),
                        Instant.parse("2026-06-25T10:00:00Z")
                ));
        when(transferExecutor.execute(any())).thenReturn(new TransferResult(
                "ivan",
                "olga",
                new BigDecimal("900.00"),
                "USD"
        ));

        transferService.transfer("ivan", new TransferRequest(
                "olga",
                new BigDecimal("100.00"),
                Currency.USD,
                Currency.CNY
        ), OPERATION_ID);

        var captor = ArgumentCaptor.forClass(TransferOperation.class);
        verify(transferExecutor).execute(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("100.00");
        assertThat(captor.getValue().currency()).isEqualTo(Currency.USD);
        assertThat(captor.getValue().recipientAmount()).isEqualByComparingTo("741.94");
        assertThat(captor.getValue().recipientCurrency()).isEqualTo(Currency.CNY);

        var notificationCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventPublisher, times(2)).publish(notificationCaptor.capture());
        var notifications = notificationCaptor.getAllValues();
        assertThat(notifications.get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(notifications.get(0).currency()).isEqualTo(Currency.USD);
        assertThat(notifications.get(1).amount()).isEqualByComparingTo("741.94");
        assertThat(notifications.get(1).currency()).isEqualTo(Currency.CNY);
    }

    @Test
    void shouldNotPublishEventsWhenAccountsTransferFails() {
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(transferExecutor.execute(any())).thenThrow(new AccountsClientException("Accounts service request failed"));

        assertThatThrownBy(() -> transferService.transfer("ivan", request("olga", "200.00"), OPERATION_ID))
                .isInstanceOf(AccountsClientException.class);
        verify(notificationEventPublisher, never()).publish(any());
    }

    @Test
    void shouldNotTransferWhenOperationWasBlocked() {
        when(blockerClient.check(any()))
                .thenReturn(new OperationCheckResponse(false, "Operation amount exceeds blocker limit"));

        assertThatThrownBy(() -> transferService.transfer("ivan", request("olga", "100000.01"), OPERATION_ID))
                .isInstanceOf(OperationBlockedException.class)
                .hasMessage("Operation amount exceeds blocker limit");

        verify(exchangeClient, never()).convert(any(), any(), any());
        verify(transferExecutor, never()).execute(any());
        verify(notificationEventPublisher, never()).publish(any());
    }

    @Test
    void shouldCheckBlockerBeforeAccountsTransfer() {
        when(blockerClient.check(any())).thenReturn(new OperationCheckResponse(true, null));
        when(transferExecutor.execute(any())).thenReturn(new TransferResult(
                "ivan",
                "olga",
                new BigDecimal("800.00"),
                "RUB"
        ));

        transferService.transfer("ivan", request("olga", "200.00"), OPERATION_ID);

        var ordered = inOrder(blockerClient, transferExecutor);
        ordered.verify(blockerClient).check(any());
        ordered.verify(transferExecutor).execute(any());
    }

    @Test
    void shouldRejectSelfTransfer() {
        assertThatThrownBy(() -> transferService.transfer("ivan", request("ivan", "200.00"), OPERATION_ID))
                .isInstanceOf(SelfTransferForbiddenException.class);
        verify(blockerClient, never()).check(any());
        verify(exchangeClient, never()).convert(any(), any(), any());
        verify(transferExecutor, never()).execute(any());
        verify(notificationEventPublisher, never()).publish(any());
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> transferService.transfer("ivan", request("olga", "0.00"), OPERATION_ID))
                .isInstanceOf(InvalidAmountException.class);
        verify(blockerClient, never()).check(any());
        verify(exchangeClient, never()).convert(any(), any(), any());
        verify(transferExecutor, never()).execute(any());
        verify(notificationEventPublisher, never()).publish(any());
    }

    @Test
    void shouldRejectAmountWithInvalidScale() {
        assertThatThrownBy(() -> transferService.transfer("ivan", request("olga", "100.001"), OPERATION_ID))
                .isInstanceOf(InvalidAmountScaleException.class);
        verify(blockerClient, never()).check(any());
        verify(exchangeClient, never()).convert(any(), any(), any());
        verify(transferExecutor, never()).execute(any());
        verify(notificationEventPublisher, never()).publish(any());
    }

    private TransferRequest request(String recipientLogin, String amount) {
        return new TransferRequest(recipientLogin, new BigDecimal(amount), Currency.RUB);
    }
}
