import org.springframework.cloud.contract.spec.Contract

Contract.make {
    name "create_transfer"
    request {
        method POST()
        url "/api/transfers"
        headers {
            contentType applicationJson()
            header "Authorization", "Bearer token"
        }
        body(
                recipientLogin: "petr",
                amount: "150.00",
                currency: "RUB"
        )
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body(
                senderLogin: "ivan",
                recipientLogin: "petr",
                senderBalance: "850.00",
                currency: "RUB",
                message: "Transfer completed"
        )
    }
}
