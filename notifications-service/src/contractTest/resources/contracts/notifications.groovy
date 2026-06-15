import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "create_notification"
    request {
        method POST()
        url "/api/notifications"
        headers {
            contentType applicationJson()
        }
        body(
                recipientLogin: "ivan",
                type: "CASH_DEPOSIT",
                message: "Счёт пополнен на 250.00 RUB",
                operationId: "operation-1"
        )
    }
    response {
        status ACCEPTED()
        headers {
            contentType applicationJson()
        }
        body(
                status: "ACCEPTED"
        )
    }
}
