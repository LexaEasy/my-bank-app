import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "notification_event_consumer"
    label "notification_event_consumer"
    input {
        triggeredBy("notificationReceived()")
    }
    outputMessage {
        sentTo "bank.notifications"
        body([
                eventId       : "33333333-3333-3333-3333-333333333333",
                operationId   : "44444444-4444-4444-4444-444444444444",
                source        : "CASH",
                type          : "CASH_DEPOSITED",
                recipientLogin: "ivan",
                message       : "Счёт пополнен на 100.00 RUB",
                occurredAt    : "2026-07-01T05:01:00Z",
                amount        : "100.00",
                currency      : "RUB"
        ])
        headers {
            messagingContentType(applicationJson())
            header("kafka_messageKey", "ivan")
        }
    }
}
