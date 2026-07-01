package ru.practicum.bank.accounts.contract;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;
import java.util.Map;

@SpringBootTest(classes = AccountsNotificationMessagingContractBase.MessagingConfiguration.class)
@AutoConfigureMessageVerifier
public abstract class AccountsNotificationMessagingContractBase {

    @Autowired
    @Qualifier("bank.notifications")
    private MessageChannel notificationsChannel;

    public void accountUpdated() {
        var event = Map.of(
                "eventId", "11111111-1111-1111-1111-111111111111",
                "operationId", "22222222-2222-2222-2222-222222222222",
                "source", "ACCOUNTS",
                "type", "ACCOUNT_UPDATED",
                "recipientLogin", "ivan",
                "message", "Профиль обновлён",
                "occurredAt", "2026-07-01T05:00:00Z"
        );
        notificationsChannel.send(MessageBuilder.withPayload(event)
                .setHeader("kafka_messageKey", "ivan")
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                .build());
    }

    @Configuration
    static class MessagingConfiguration {

        @Bean(name = "bank.notifications")
        QueueChannel notificationsChannel() {
            return new QueueChannel();
        }
    }
}
