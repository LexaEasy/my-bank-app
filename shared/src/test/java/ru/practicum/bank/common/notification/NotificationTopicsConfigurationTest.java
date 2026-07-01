package ru.practicum.bank.common.notification;

import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTopicsConfigurationTest {

    private final NotificationTopicsConfiguration configuration =
            new NotificationTopicsConfiguration();

    @Test
    void shouldDeclareNotificationTopicsWithRequiredSettings() {
        var notificationsTopic = configuration.notificationsTopic("bank.notifications");
        var dltTopic = configuration.notificationsDltTopic("bank.notifications.dlt");

        assertThat(notificationsTopic.name()).isEqualTo("bank.notifications");
        assertThat(notificationsTopic.numPartitions()).isEqualTo(3);
        assertThat(notificationsTopic.replicationFactor()).isEqualTo((short) 1);
        assertThat(notificationsTopic.configs()).isNull();

        assertThat(dltTopic.name()).isEqualTo("bank.notifications.dlt");
        assertThat(dltTopic.numPartitions()).isEqualTo(3);
        assertThat(dltTopic.replicationFactor()).isEqualTo((short) 1);
        assertThat(dltTopic.configs())
                .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "604800000");
    }
}
