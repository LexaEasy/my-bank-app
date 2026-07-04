package ru.practicum.bank.notifications.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigurationTest {

    private final KafkaConsumerConfiguration configuration = new KafkaConsumerConfiguration();

    @Test
    void shouldAcknowledgeRecordOnlyAfterSuccessfulRecovery() {
        var errorHandler = configuration.kafkaErrorHandler(mock(DeadLetterPublishingRecoverer.class));

        assertThat(errorHandler.isAckAfterHandle()).isTrue();
    }
}
