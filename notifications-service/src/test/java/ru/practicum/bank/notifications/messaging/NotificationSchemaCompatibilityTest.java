package ru.practicum.bank.notifications.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.common.notification.NotificationType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationSchemaCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldReadAccountUpdatedEventWithoutMoneyFields() throws Exception {
        var compatibleJson = """
                {
                  "eventId": "33333333-3333-3333-3333-333333333333",
                  "operationId": "44444444-4444-4444-4444-444444444444",
                  "source": "ACCOUNTS",
                  "type": "ACCOUNT_UPDATED",
                  "recipientLogin": "ivan",
                  "message": "Данные профиля обновлены",
                  "occurredAt": "2026-07-01T05:01:00Z"
                }
                """;

        var event = objectMapper.readValue(compatibleJson, NotificationEvent.class);

        assertThat(event.type()).isEqualTo(NotificationType.ACCOUNT_UPDATED);
        assertThat(event.amount()).isNull();
        assertThat(event.currency()).isNull();
    }

    @Test
    void shouldRejectUnknownEventType() {
        var incompatibleJson = """
                {
                  "eventId": "33333333-3333-3333-3333-333333333333",
                  "operationId": "44444444-4444-4444-4444-444444444444",
                  "source": "CASH",
                  "type": "CASH_REVERSED",
                  "recipientLogin": "ivan",
                  "message": "Операция отменена",
                  "occurredAt": "2026-07-01T05:01:00Z"
                }
                """;

        assertThatThrownBy(() -> objectMapper.readValue(incompatibleJson, NotificationEvent.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class)
                .hasMessageContaining("CASH_REVERSED");
    }
}
