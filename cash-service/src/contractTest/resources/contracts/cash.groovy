import org.springframework.cloud.contract.spec.Contract

[
        Contract.make {
            name "deposit_cash"
            request {
                method POST()
                url "/api/cash/deposit"
                headers {
                    contentType applicationJson()
                    header "Authorization", "Bearer token"
                }
                body(
                        amount: "250.00",
                        currency: "RUB"
                )
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body(
                        balance: "1250.00",
                        currency: "RUB",
                        message: "Счёт пополнен"
                )
            }
        },
        Contract.make {
            name "withdraw_cash"
            request {
                method POST()
                url "/api/cash/withdraw"
                headers {
                    contentType applicationJson()
                    header "Authorization", "Bearer token"
                }
                body(
                        amount: "100.00",
                        currency: "RUB"
                )
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body(
                        balance: "900.00",
                        currency: "RUB",
                        message: "Деньги сняты со счёта"
                )
            }
        }
]
