import org.springframework.cloud.contract.spec.Contract

[
        Contract.make {
            name "deposit_internal_balance"
            request {
                method POST()
                url "/api/accounts/internal/balance/deposit"
                headers {
                    contentType applicationJson()
                }
                body(
                        login: "ivan",
                        amount: "250.00",
                        currency: "RUB",
                        operationId: "operation-1"
                )
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body(
                        login: "ivan",
                        balance: "1250.00",
                        currency: "RUB"
                )
            }
        },
        Contract.make {
            name "withdraw_internal_balance"
            request {
                method POST()
                url "/api/accounts/internal/balance/withdraw"
                headers {
                    contentType applicationJson()
                }
                body(
                        login: "ivan",
                        amount: "100.00",
                        currency: "RUB",
                        operationId: "operation-2"
                )
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body(
                        login: "ivan",
                        balance: "900.00",
                        currency: "RUB"
                )
            }
        },
        Contract.make {
            name "transfer_internal_balance"
            request {
                method POST()
                url "/api/accounts/internal/balance/transfer"
                headers {
                    contentType applicationJson()
                }
                body(
                        senderLogin: "ivan",
                        recipientLogin: "petr",
                        amount: "150.00",
                        currency: "RUB",
                        operationId: "operation-3"
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
                        currency: "RUB"
                )
            }
        }
]
