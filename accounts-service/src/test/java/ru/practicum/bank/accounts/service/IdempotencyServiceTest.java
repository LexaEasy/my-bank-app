package ru.practicum.bank.accounts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.practicum.bank.accounts.dto.BalanceOperationRequest;
import ru.practicum.bank.accounts.dto.BalanceResponse;
import ru.practicum.bank.accounts.exception.IdempotencyConflictException;
import ru.practicum.bank.accounts.exception.OperationInProgressException;
import ru.practicum.bank.accounts.model.Currency;
import ru.practicum.bank.accounts.model.ProcessedOperation;
import ru.practicum.bank.accounts.model.ProcessedOperationStatus;
import ru.practicum.bank.accounts.repository.ProcessedOperationRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
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
    private Clock clock;

    @Autowired
    private ObjectMapper objectMapper;

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
                hashRequest(request),
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
    void shouldSaveFailedStatusWhenBusinessOperationFails() {
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

        assertThat(operationRepository.findById(operationId))
                .get()
                .extracting(ProcessedOperation::getStatus)
                .isEqualTo(ProcessedOperationStatus.FAILED);
    }

    private BalanceOperationRequest request(String operationId, String login, String amount) {
        return new BalanceOperationRequest(login, new BigDecimal(amount), Currency.RUB, operationId);
    }

    private String hashRequest(Object request) {
        try {
            var mapper = objectMapper.copy()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
