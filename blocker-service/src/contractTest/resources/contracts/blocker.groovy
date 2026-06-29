import org.springframework.cloud.contract.spec.Contract

[
        Contract.make {
            name "allow_cash_operation"
            request {
                method POST()
                url "/api/blocker/check"
                headers {
                    contentType applicationJson()
                }
                body(
                        operationId  : "op-1",
                        operationType: "DEPOSIT",
                        login        : "ivan",
                        amount       : "1000.00",
                        currency     : "RUB"
                )
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body(
                        allowed: true,
                        reason : null
                )
            }
        },
        Contract.make {
            name "block_transfer_above_limit"
            request {
                method POST()
                url "/api/blocker/check"
                headers {
                    contentType applicationJson()
                }
                body(
                        operationId  : "op-2",
                        operationType: "TRANSFER",
                        sender       : "ivan",
                        recipient    : "olga",
                        amount       : "100000.01",
                        currency     : "USD"
                )
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body(
                        allowed: false,
                        reason : "Operation amount exceeds blocker limit"
                )
            }
        },
        Contract.make {
            name "reject_cash_operation_without_login"
            request {
                method POST()
                url "/api/blocker/check"
                headers {
                    contentType applicationJson()
                }
                body(
                        operationId  : "op-3",
                        operationType: "WITHDRAW",
                        amount       : "1000.00",
                        currency     : "RUB"
                )
            }
            response {
                status BAD_REQUEST()
                headers {
                    contentType applicationJson()
                }
                body(
                        code   : "INVALID_OPERATION_REQUEST",
                        message: "login is required for cash operation"
                )
            }
        }
]
