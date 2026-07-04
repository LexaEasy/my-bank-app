package ru.practicum.bank.cash.contract;

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

@SpringBootTest(classes = CashNotificationMessagingContractBase.MessagingConfiguration.class)
@AutoConfigureMessageVerifier
public abstract class CashNotificationMessagingContractBase {

    @Autowired
    @Qualifier("bank.notifications")
    private MessageChannel notificationsChannel;

    public void cashDeposited() {
        var event = Map.of(
                "eventId", "33333333-3333-3333-3333-333333333333",
                "operationId", "44444444-4444-4444-4444-444444444444",
                "source", "CASH",
                "type", "CASH_DEPOSITED",
                "recipientLogin", "ivan",
                "message", "Счёт пополнен на 100.00 RUB",
                "occurredAt", "2026-07-01T05:01:00Z",
                "amount", "100.00",
                "currency", "RUB"
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
