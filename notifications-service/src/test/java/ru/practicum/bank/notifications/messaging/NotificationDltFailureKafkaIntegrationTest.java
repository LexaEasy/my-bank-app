package ru.practicum.bank.notifications.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.practicum.bank.notifications.service.NotificationService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.practicum.bank.common.notification.NotificationTopicsProperties.DEFAULT_PARTITION_COUNT;

@SpringBootTest(properties = {
        "spring.kafka.admin.fail-fast=true",
        "spring.kafka.consumer.group-id=" + KafkaIntegrationTestSupport.GROUP_ID
})
@EmbeddedKafka(
        kraft = true,
        partitions = DEFAULT_PARTITION_COUNT,
        topics = {KafkaIntegrationTestSupport.TOPIC, KafkaIntegrationTestSupport.DLT_TOPIC},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class NotificationDltFailureKafkaIntegrationTest extends KafkaIntegrationTestSupport {

    @MockitoBean
    NotificationService notificationService;

    @MockitoBean(name = "dltKafkaTemplate")
    KafkaTemplate<Object, Object> dltKafkaTemplate;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Test
    void shouldNotCommitSourceOffsetWhenDltPublishingFails() throws Exception {
        UUID eventId = UUID.randomUUID();
        doThrow(new IllegalStateException("processing failure"))
                .when(notificationService)
                .notify(argThat(event -> event.eventId().equals(eventId)));
        when(dltKafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<Object, Object>>any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("DLT unavailable")));

        var result = eventTemplate().send(TOPIC, "dlt-failure", event(eventId, "dlt-failure"))
                .get(KAFKA_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        int partition = result.getRecordMetadata().partition();

        verify(dltKafkaTemplate, timeout(10_000).atLeastOnce())
                .send(org.mockito.ArgumentMatchers.<ProducerRecord<Object, Object>>any());
        registry.stop();

        assertThat(committedOffset(partition))
                .isLessThanOrEqualTo(result.getRecordMetadata().offset());
    }
}
