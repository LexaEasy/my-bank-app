package ru.practicum.bank.notifications.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigurationTest {

    private final KafkaConsumerConfiguration configuration = new KafkaConsumerConfiguration();

    @Test
    void shouldAcknowledgeRecordOnlyAfterSuccessfulRecovery() {
        var errorHandler = configuration.kafkaErrorHandler(mock(DeadLetterPublishingRecoverer.class));

        assertThat(errorHandler.isAckAfterHandle()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldEnableObservationForDltTemplate() {
        var kafkaTemplate = configuration.dltKafkaTemplate(mock(ProducerFactory.class));

        assertThat(ReflectionTestUtils.getField(kafkaTemplate, "observationEnabled")).isEqualTo(true);
    }
}
