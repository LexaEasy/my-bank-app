package ru.practicum.bank.common.notification;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import ru.practicum.bank.common.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEventTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void shouldPreserveEventDuringJsonRoundTrip() throws Exception {
        var event = new NotificationEvent(
                UUID.fromString("10cb8eb2-b488-4f62-b139-a07314cc3ef4"),
                UUID.fromString("3e3a3fec-843e-44e2-bcf5-3bea12845327"),
                NotificationSource.CASH,
                NotificationType.CASH_DEPOSITED,
                "ivan",
                "Счёт пополнен",
                Instant.parse("2026-06-30T05:00:00Z"),
                new BigDecimal("250.00"),
                Currency.RUB
        );

        String json = objectMapper.writeValueAsString(event);
        var restoredEvent = objectMapper.readValue(json, NotificationEvent.class);

        assertThat(restoredEvent).isEqualTo(event);
        assertThat(json)
                .contains("\"amount\":\"250.00\"")
                .doesNotContain("jwt", "password", "credentials");
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        var event = new NotificationEvent(
                null,
                null,
                null,
                null,
                " ",
                "",
                null,
                null,
                null
        );

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(event);

            assertThat(violations)
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder(
                            "eventId",
                            "operationId",
                            "source",
                            "type",
                            "recipientLogin",
                            "message",
                            "occurredAt"
                    );
        }
    }

    @Test
    void shouldRejectUnknownEventType() {
        String json = """
                {
                  "eventId": "10cb8eb2-b488-4f62-b139-a07314cc3ef4",
                  "operationId": "3e3a3fec-843e-44e2-bcf5-3bea12845327",
                  "source": "CASH",
                  "type": "UNKNOWN_TYPE",
                  "recipientLogin": "ivan",
                  "message": "Событие",
                  "occurredAt": "2026-06-30T05:00:00Z"
                }
                """;

        assertThatThrownBy(() -> objectMapper.readValue(json, NotificationEvent.class))
                .hasMessageContaining("UNKNOWN_TYPE");
    }
}
