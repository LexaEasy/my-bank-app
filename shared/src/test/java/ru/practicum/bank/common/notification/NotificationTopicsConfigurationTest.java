package ru.practicum.bank.common.notification;

import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.practicum.bank.common.notification.NotificationTopicsProperties.DEFAULT_DLT_RETENTION_MS;
import static ru.practicum.bank.common.notification.NotificationTopicsProperties.DEFAULT_PARTITION_COUNT;
import static ru.practicum.bank.common.notification.NotificationTopicsProperties.DEFAULT_REPLICATION_FACTOR;

class NotificationTopicsConfigurationTest {

    private final NotificationTopicsConfiguration configuration =
            new NotificationTopicsConfiguration();

    @Test
    void shouldDeclareNotificationTopicsWithRequiredSettings() {
        var properties = defaultProperties();
        var notificationsTopic = configuration.notificationsTopic(properties);
        var dltTopic = configuration.notificationsDltTopic(properties);

        assertThat(notificationsTopic.name()).isEqualTo("bank.notifications");
        assertThat(notificationsTopic.numPartitions()).isEqualTo(DEFAULT_PARTITION_COUNT);
        assertThat(notificationsTopic.replicationFactor()).isEqualTo(DEFAULT_REPLICATION_FACTOR);
        assertThat(notificationsTopic.configs()).isNull();

        assertThat(dltTopic.name()).isEqualTo("bank.notifications.dlt");
        assertThat(dltTopic.numPartitions()).isEqualTo(DEFAULT_PARTITION_COUNT);
        assertThat(dltTopic.replicationFactor()).isEqualTo(DEFAULT_REPLICATION_FACTOR);
        assertThat(dltTopic.configs())
                .containsEntry(TopicConfig.RETENTION_MS_CONFIG, Long.toString(DEFAULT_DLT_RETENTION_MS));
    }

    @Test
    void shouldDeclareTopicsWithConfiguredTopology() {
        var properties = defaultProperties();
        properties.setNotificationsPartitions(4);
        properties.setNotificationsDltPartitions(5);
        properties.setNotificationsReplicationFactor((short) 2);
        properties.setNotificationsDltRetentionMs(86_400_000L);

        var notificationsTopic = configuration.notificationsTopic(properties);
        var dltTopic = configuration.notificationsDltTopic(properties);

        assertThat(notificationsTopic.numPartitions()).isEqualTo(4);
        assertThat(notificationsTopic.replicationFactor()).isEqualTo((short) 2);
        assertThat(dltTopic.numPartitions()).isEqualTo(5);
        assertThat(dltTopic.replicationFactor()).isEqualTo((short) 2);
        assertThat(dltTopic.configs())
                .containsEntry(TopicConfig.RETENTION_MS_CONFIG, "86400000");
    }

    @Test
    void shouldFailStartupWhenDltHasFewerPartitionsThanMainTopic() {
        new ApplicationContextRunner()
                .withUserConfiguration(NotificationTopicsConfiguration.class)
                .withPropertyValues(
                        "bank.kafka.notifications-topic=bank.notifications",
                        "bank.kafka.notifications-dlt-topic=bank.notifications.dlt",
                        "bank.kafka.notifications-partitions=4",
                        "bank.kafka.notifications-dlt-partitions=3"
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining(
                                "bank.kafka.notifications-dlt-partitions must be greater than or equal to "
                                        + "bank.kafka.notifications-partitions"
                        ));
    }

    @Test
    void shouldFailStartupWhenNotificationsTopicIsBlank() {
        new ApplicationContextRunner()
                .withUserConfiguration(NotificationTopicsConfiguration.class)
                .withPropertyValues(
                        "bank.kafka.notifications-topic=   ",
                        "bank.kafka.notifications-dlt-topic=bank.notifications.dlt"
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining("bank.kafka.notifications-topic must not be blank"));
    }

    @Test
    void shouldFailStartupWhenNotificationsDltTopicIsBlank() {
        new ApplicationContextRunner()
                .withUserConfiguration(NotificationTopicsConfiguration.class)
                .withPropertyValues(
                        "bank.kafka.notifications-topic=bank.notifications",
                        "bank.kafka.notifications-dlt-topic=   "
                )
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining("bank.kafka.notifications-dlt-topic must not be blank"));
    }

    private NotificationTopicsProperties defaultProperties() {
        var properties = new NotificationTopicsProperties();
        properties.setNotificationsTopic("bank.notifications");
        properties.setNotificationsDltTopic("bank.notifications.dlt");
        return properties;
    }
}
