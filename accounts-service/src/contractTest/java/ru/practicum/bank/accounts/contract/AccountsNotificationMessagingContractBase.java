package ru.practicum.bank.accounts.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import ru.practicum.bank.accounts.notification.AccountUpdatedNotificationFactory;

import java.time.Instant;
import java.util.UUID;

@SpringBootTest(classes = AccountsNotificationMessagingContractBase.MessagingConfiguration.class)
@AutoConfigureMessageVerifier
public abstract class AccountsNotificationMessagingContractBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final AccountUpdatedNotificationFactory NOTIFICATION_FACTORY =
            new AccountUpdatedNotificationFactory();

    @Autowired
    @Qualifier("bank.notifications")
    private MessageChannel notificationsChannel;

    public void accountUpdated() throws Exception {
        var event = NOTIFICATION_FACTORY.create(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "ivan",
                Instant.parse("2026-07-01T05:00:00Z")
        );
        var payload = OBJECT_MAPPER.readValue(
                OBJECT_MAPPER.writeValueAsString(event),
                new TypeReference<Object>() {
                }
        );

        notificationsChannel.send(MessageBuilder.withPayload(payload)
                .setHeader("kafka_messageKey", event.recipientLogin())
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
