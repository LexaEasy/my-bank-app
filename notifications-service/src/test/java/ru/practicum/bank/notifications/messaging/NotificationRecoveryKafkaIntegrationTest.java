package ru.practicum.bank.notifications.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.bank.notifications.service.NotificationService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.kafka.admin.fail-fast=true",
        "spring.kafka.consumer.group-id=" + KafkaIntegrationTestSupport.GROUP_ID
})
@EmbeddedKafka(
        kraft = true,
        partitions = 3,
        topics = {KafkaIntegrationTestSupport.TOPIC, KafkaIntegrationTestSupport.DLT_TOPIC},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationRecoveryKafkaIntegrationTest extends KafkaIntegrationTestSupport {

    @MockitoBean
    NotificationService notificationService;

    @Test
    void shouldRetryExactlyThreeTimesPublishToDltAndCommitSourceOffset() throws Exception {
        UUID eventId = UUID.randomUUID();
        doThrow(new IllegalStateException("temporary failure"))
                .when(notificationService)
                .notify(argThat(event -> event.eventId().equals(eventId)));

        try (var dltConsumer = dltConsumer("retry-dlt-" + eventId)) {
            var result = eventTemplate().send(TOPIC, "retry-user", event(eventId, "retry-user")).get();
            var dltRecord = awaitRecord(dltConsumer, record -> "retry-user".equals(record.key()));

            assertThat(dltRecord.value()).isNotEmpty();
            verify(notificationService, after(10_000).times(3))
                    .notify(argThat(event -> event.eventId().equals(eventId)));
            assertThat(committedOffset(result.getRecordMetadata().partition()))
                    .isGreaterThanOrEqualTo(result.getRecordMetadata().offset() + 1);
        }
    }

    @Test
    void shouldPublishMalformedJsonToDltWithoutInvokingService() throws Exception {
        String key = "malformed-" + UUID.randomUUID();
        byte[] malformedJson = "{not-json".getBytes(StandardCharsets.UTF_8);

        try (var dltConsumer = dltConsumer("malformed-dlt-" + key)) {
            byteTemplate().send(TOPIC, key, malformedJson).get();
            var dltRecord = awaitRecord(dltConsumer, record -> key.equals(record.key()));

            assertThat(dltRecord.value()).isEqualTo(malformedJson);
            verify(notificationService, after(2_000).never()).notify(
                    argThat(event -> key.equals(event.recipientLogin()))
            );
        }
    }

    @Test
    void shouldPublishInvalidMoneyEventToDltWithoutRetryAndCommitSourceOffset() throws Exception {
        UUID eventId = UUID.randomUUID();
        String key = "invalid-money-" + eventId;
        var event = new ru.practicum.bank.common.notification.NotificationEvent(
                eventId,
                UUID.randomUUID(),
                ru.practicum.bank.common.notification.NotificationSource.CASH,
                ru.practicum.bank.common.notification.NotificationType.CASH_DEPOSITED,
                key,
                "Account replenished",
                Instant.parse("2026-06-30T05:00:00Z"),
                null,
                null
        );

        try (var dltConsumer = dltConsumer("invalid-money-dlt-" + eventId)) {
            var result = eventTemplate().send(TOPIC, key, event).get();
            var dltRecord = awaitRecord(dltConsumer, record -> key.equals(record.key()));

            assertThat(dltRecord.value()).isNotEmpty();
            verify(notificationService, after(2_000).never())
                    .notify(argThat(notification -> notification.eventId().equals(eventId)));
            assertThat(committedOffset(result.getRecordMetadata().partition()))
                    .isGreaterThanOrEqualTo(result.getRecordMetadata().offset() + 1);
        }
    }
}
