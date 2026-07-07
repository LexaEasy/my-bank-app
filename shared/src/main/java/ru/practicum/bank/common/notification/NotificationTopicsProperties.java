package ru.practicum.bank.common.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "bank.kafka")
public class NotificationTopicsProperties implements InitializingBean {

    public static final int DEFAULT_PARTITION_COUNT = 3;
    public static final short DEFAULT_REPLICATION_FACTOR = 1;
    public static final long DEFAULT_DLT_RETENTION_MS = 604_800_000L;

    private String notificationsTopic;
    private String notificationsDltTopic;
    private int notificationsPartitions = DEFAULT_PARTITION_COUNT;
    private int notificationsDltPartitions = DEFAULT_PARTITION_COUNT;
    private short notificationsReplicationFactor = DEFAULT_REPLICATION_FACTOR;
    private long notificationsDltRetentionMs = DEFAULT_DLT_RETENTION_MS;

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    void validate() {
        if (!StringUtils.hasText(notificationsTopic)) {
            throw new IllegalStateException("bank.kafka.notifications-topic must not be blank");
        }
        if (!StringUtils.hasText(notificationsDltTopic)) {
            throw new IllegalStateException("bank.kafka.notifications-dlt-topic must not be blank");
        }
        if (notificationsPartitions < 1) {
            throw new IllegalStateException("bank.kafka.notifications-partitions must be positive");
        }
        if (notificationsDltPartitions < 1) {
            throw new IllegalStateException("bank.kafka.notifications-dlt-partitions must be positive");
        }
        if (notificationsDltPartitions < notificationsPartitions) {
            throw new IllegalStateException(
                    "bank.kafka.notifications-dlt-partitions must be greater than or equal to "
                            + "bank.kafka.notifications-partitions"
            );
        }
        if (notificationsReplicationFactor < 1) {
            throw new IllegalStateException("bank.kafka.notifications-replication-factor must be positive");
        }
        if (notificationsDltRetentionMs < 1) {
            throw new IllegalStateException("bank.kafka.notifications-dlt-retention-ms must be positive");
        }
    }

    public String getNotificationsTopic() {
        return notificationsTopic;
    }

    public void setNotificationsTopic(String notificationsTopic) {
        this.notificationsTopic = notificationsTopic;
    }

    public String getNotificationsDltTopic() {
        return notificationsDltTopic;
    }

    public void setNotificationsDltTopic(String notificationsDltTopic) {
        this.notificationsDltTopic = notificationsDltTopic;
    }

    public int getNotificationsPartitions() {
        return notificationsPartitions;
    }

    public void setNotificationsPartitions(int notificationsPartitions) {
        this.notificationsPartitions = notificationsPartitions;
    }

    public int getNotificationsDltPartitions() {
        return notificationsDltPartitions;
    }

    public void setNotificationsDltPartitions(int notificationsDltPartitions) {
        this.notificationsDltPartitions = notificationsDltPartitions;
    }

    public short getNotificationsReplicationFactor() {
        return notificationsReplicationFactor;
    }

    public void setNotificationsReplicationFactor(short notificationsReplicationFactor) {
        this.notificationsReplicationFactor = notificationsReplicationFactor;
    }

    public long getNotificationsDltRetentionMs() {
        return notificationsDltRetentionMs;
    }

    public void setNotificationsDltRetentionMs(long notificationsDltRetentionMs) {
        this.notificationsDltRetentionMs = notificationsDltRetentionMs;
    }
}
