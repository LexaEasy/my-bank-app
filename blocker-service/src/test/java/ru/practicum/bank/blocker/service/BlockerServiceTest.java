package ru.practicum.bank.blocker.service;

import org.junit.jupiter.api.Test;
import ru.practicum.bank.blocker.config.BlockerProperties;
import ru.practicum.bank.blocker.exception.InvalidOperationRequestException;
import ru.practicum.bank.common.dto.blocker.OperationCheckRequest;
import ru.practicum.bank.common.model.Currency;
import ru.practicum.bank.common.model.OperationType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockerServiceTest {

    private final BlockerService blockerService = new BlockerService(new BlockerProperties(new BigDecimal("100000.00")));

    @Test
    void shouldAllowOperationWithinLimit() {
        var response = blockerService.check(cashRequest(new BigDecimal("1000.00")));

        assertThat(response.allowed()).isTrue();
        assertThat(response.reason()).isNull();
    }

    @Test
    void shouldBlockOperationAboveLimit() {
        var response = blockerService.check(cashRequest(new BigDecimal("100000.01")));

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("Operation amount exceeds blocker limit");
    }

    @Test
    void shouldRequireLoginForCashOperation() {
        var request = new OperationCheckRequest(
                "op-1",
                OperationType.WITHDRAW,
                null,
                null,
                null,
                new BigDecimal("1000.00"),
                Currency.RUB
        );

        assertThatThrownBy(() -> blockerService.check(request))
                .isInstanceOf(InvalidOperationRequestException.class)
                .hasMessage("login is required for cash operation");
    }

    @Test
    void shouldRequireParticipantsForTransfer() {
        var request = new OperationCheckRequest(
                "op-1",
                OperationType.TRANSFER,
                null,
                "ivan",
                null,
                new BigDecimal("1000.00"),
                Currency.USD
        );

        assertThatThrownBy(() -> blockerService.check(request))
                .isInstanceOf(InvalidOperationRequestException.class)
                .hasMessage("recipient is required for transfer");
    }

    private OperationCheckRequest cashRequest(BigDecimal amount) {
        return new OperationCheckRequest(
                "op-1",
                OperationType.DEPOSIT,
                "ivan",
                null,
                null,
                amount,
                Currency.RUB
        );
    }
}
