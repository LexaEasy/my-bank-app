import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "account_updated_notification"
    label "account_updated_notification"
    input {
        triggeredBy("accountUpdated()")
    }
    outputMessage {
        sentTo "bank.notifications"
        body([
                eventId       : "11111111-1111-1111-1111-111111111111",
                operationId   : "22222222-2222-2222-2222-222222222222",
                source        : "ACCOUNTS",
                type          : "ACCOUNT_UPDATED",
                recipientLogin: "ivan",
                message       : "Р”Р°РЅРЅС‹Рµ РїСЂРѕС„РёР»СЏ РѕР±РЅРѕРІР»РµРЅС‹",
                occurredAt    : "2026-07-01T05:00:00Z",
                amount        : null,
                currency      : null
        ])
        headers {
            messagingContentType(applicationJson())
            header("kafka_messageKey", "ivan")
        }
    }
}
