package ru.practicum.bank.notifications.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import ru.practicum.bank.common.notification.NotificationEvent;
import ru.practicum.bank.notifications.messaging.NotificationEventListener;
import ru.practicum.bank.notifications.service.NotificationService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = NotificationsMessagingContractBase.MessagingConfiguration.class)
@AutoConfigureMessageVerifier
public abstract class NotificationsMessagingContractBase {

    private static final String EVENT_JSON = """
            {
              "eventId": "33333333-3333-3333-3333-333333333333",
              "operationId": "44444444-4444-4444-4444-444444444444",
              "source": "CASH",
              "type": "CASH_DEPOSITED",
              "recipientLogin": "ivan",
              "message": "Счёт пополнен на 100.00 RUB",
              "occurredAt": "2026-07-01T05:01:00Z",
              "amount": "100.00",
              "currency": "RUB"
            }
            """;

    @Autowired
    @Qualifier("bank.notifications")
    private MessageChannel notificationsChannel;

    public void notificationReceived() throws Exception {
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var event = objectMapper.readValue(EVENT_JSON, NotificationEvent.class);
        var notificationService = mock(NotificationService.class);
        var listener = new NotificationEventListener(
                notificationService,
                Validation.buildDefaultValidatorFactory().getValidator()
        );

        listener.listen(new ConsumerRecord<>(
                "bank.notifications",
                0,
                0L,
                event.recipientLogin(),
                event
        ));
        verify(notificationService).notify(event);

        var payload = objectMapper.readValue(EVENT_JSON, new TypeReference<Object>() {
        });
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
