package ru.practicum.bank.transfer.contract;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

@SpringBootTest(classes = TransferNotificationMessagingContractBase.MessagingConfiguration.class)
@AutoConfigureMessageVerifier
public abstract class TransferNotificationMessagingContractBase {

    @Autowired
    @Qualifier("bank.notifications")
    private MessageChannel notificationsChannel;

    public void transferIncoming() {
        var event = Map.of(
                "eventId", "55555555-5555-5555-5555-555555555555",
                "operationId", "66666666-6666-6666-6666-666666666666",
                "source", "TRANSFER",
                "type", "TRANSFER_INCOMING",
                "recipientLogin", "olga",
                "message", "Получен перевод от ivan: 741.94 CNY",
                "occurredAt", "2026-07-01T05:02:00Z",
                "amount", "741.94",
                "currency", "CNY"
        );
        notificationsChannel.send(MessageBuilder.withPayload(event)
                .setHeader("kafka_messageKey", "olga")
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
