package ru.practicum.bank.notifications.messaging;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.bank.notifications.service.NotificationService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
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
class NotificationConsumerKafkaIntegrationTest extends KafkaIntegrationTestSupport {

    @MockitoBean
    NotificationService notificationService;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Test
    void shouldDeclareTopicsWithRequiredSettings() throws Exception {
        try (var admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()
        ))) {
            var descriptions = admin.describeTopics(java.util.List.of(TOPIC, DLT_TOPIC))
                    .allTopicNames()
                    .get(10, TimeUnit.SECONDS);
            assertThat(descriptions.get(TOPIC).partitions()).hasSize(3);
            assertThat(descriptions.get(DLT_TOPIC).partitions()).hasSize(3);

            var dltResource = new ConfigResource(ConfigResource.Type.TOPIC, DLT_TOPIC);
            var dltConfig = admin.describeConfigs(java.util.List.of(dltResource))
                    .all()
                    .get(10, TimeUnit.SECONDS)
                    .get(dltResource);
            assertThat(dltConfig.get(TopicConfig.RETENTION_MS_CONFIG).value())
                    .isEqualTo("604800000");
        }
    }

    @Test
    void shouldCommitSuccessfulRecordAndNotReadItAfterRestart() throws Exception {
        UUID eventId = UUID.randomUUID();
        var result = eventTemplate().send(TOPIC, "ivan", event(eventId, "ivan")).get();

        verify(notificationService, after(10_000))
                .notify(argThat(event -> event.eventId().equals(eventId)));
        int partition = result.getRecordMetadata().partition();
        long expectedOffset = result.getRecordMetadata().offset() + 1;
        assertThat(committedOffset(partition))
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
