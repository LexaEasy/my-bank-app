package ru.practicum.bank.notifications.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.bank.notifications.service.NotificationService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.kafka.consumer.group-id=" + KafkaIntegrationTestSupport.GROUP_ID)
@EmbeddedKafka(
        kraft = true,
        partitions = 3,
        topics = {KafkaIntegrationTestSupport.TOPIC, KafkaIntegrationTestSupport.DLT_TOPIC},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class NotificationConsumerKafkaIntegrationTest extends KafkaIntegrationTestSupport {

    @MockitoBean
    NotificationService notificationService;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Test
    void shouldCommitSuccessfulRecordAndNotReadItAfterRestart() throws Exception {
        UUID eventId = UUID.randomUUID();
        var result = eventTemplate().send(TOPIC, "ivan", event(eventId, "ivan")).get();

        verify(notificationService, after(10_000))
                .notify(argThat(event -> event.eventId().equals(eventId)));
        int partition = result.getRecordMetadata().partition();
        long expectedOffset = result.getRecordMetadata().offset() + 1;
        org.assertj.core.api.Assertions.assertThat(committedOffset(partition))
                .isGreaterThanOrEqualTo(expectedOffset);

        clearInvocations(notificationService);
        registry.stop();
        registry.start();

        verify(notificationService, after(2_000).never()).notify(
                argThat(event -> event.eventId().equals(eventId))
        );
    }

    @Test
    void shouldProcessRecordsCreatedWhileListenerIsStoppedAndAcceptDuplicate() throws Exception {
        registry.stop();
        UUID unreadEventId = UUID.randomUUID();
        eventTemplate().send(TOPIC, "olga", event(unreadEventId, "olga")).get();
        registry.start();

        verify(notificationService, after(10_000))
                .notify(argThat(event -> event.eventId().equals(unreadEventId)));

        UUID duplicateEventId = UUID.randomUUID();
        var duplicate = event(duplicateEventId, "petr");
        var template = eventTemplate();
        template.send(TOPIC, "petr", duplicate).get();
        template.send(TOPIC, "petr", duplicate).get();

        verify(notificationService, after(10_000).times(2))
                .notify(argThat(event -> event.eventId().equals(duplicateEventId)));
    }
}
