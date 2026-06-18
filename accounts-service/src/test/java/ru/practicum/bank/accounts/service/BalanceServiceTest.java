package ru.practicum.bank.accounts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.dto.TransferBalanceRequest;
import ru.practicum.bank.accounts.dto.TransferBalanceResponse;
import ru.practicum.bank.accounts.model.Currency;

import java.math.BigDecimal;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalanceServiceTest {

    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final BalanceOperationExecutor operationExecutor = mock(BalanceOperationExecutor.class);

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        when(idempotencyService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(4);
            return operation.get();
        });
        balanceService = new BalanceService(idempotencyService, operationExecutor);
    }

    @Test
    void shouldExecuteDepositThroughIdempotency() {
        var request = operationRequest("ivan", "250.00", "deposit-1");
        when(operationExecutor.deposit(request)).thenReturn(new BalanceResponse(
                "ivan",
                new BigDecimal("1250.00"),
                "RUB"
        ));

        var response = balanceService.deposit(request);

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("1250.00"));
        verify(idempotencyService).execute(
                eq("deposit-1"),
                eq("DEPOSIT"),
                eq(request),
                eq(BalanceResponse.class),
                any()
        );
        verify(operationExecutor).deposit(request);
    }

    @Test
    void shouldExecuteWithdrawThroughIdempotency() {
        var request = operationRequest("ivan", "100.00", "withdraw-1");
        when(operationExecutor.withdraw(request)).thenReturn(new BalanceResponse(
                "ivan",
                new BigDecimal("900.00"),
                "RUB"
        ));

        var response = balanceService.withdraw(request);

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("900.00"));
        verify(idempotencyService).execute(
                eq("withdraw-1"),
                eq("WITHDRAW"),
                eq(request),
                eq(BalanceResponse.class),
                any()
        );
        verify(operationExecutor).withdraw(request);
    }

    @Test
    void shouldExecuteTransferThroughIdempotency() {
        var request = transferRequest("ivan", "petr", "150.00", "transfer-1");
        when(operationExecutor.transfer(request)).thenReturn(new TransferBalanceResponse(
                "ivan",
                "petr",
                new BigDecimal("850.00"),
                "RUB"
        ));

        var response = balanceService.transfer(request);

        assertThat(response.senderBalance()).isEqualByComparingTo(new BigDecimal("850.00"));
        verify(idempotencyService).execute(
                eq("transfer-1"),
                eq("TRANSFER"),
                eq(request),
                eq(TransferBalanceResponse.class),
                any()
        );
        verify(operationExecutor).transfer(request);
    }

    private BalanceOperationRequest operationRequest(String login, String amount, String operationId) {
        return new BalanceOperationRequest(login, new BigDecimal(amount), Currency.RUB, operationId);
    }

    private TransferBalanceRequest transferRequest(
            String senderLogin,
            String recipientLogin,
            String amount,
            String operationId
    ) {
        return new TransferBalanceRequest(
                senderLogin,
                recipientLogin,
                new BigDecimal(amount),
                Currency.RUB,
                operationId
        );
    }
}
