import org.springframework.cloud.contract.spec.Contract

[
        Contract.make {
            name "get_exchange_rates"
            request {
                method GET()
                url "/api/exchange/rates"
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body([
                        [
                                currency : "RUB",
                                buyRate  : "1.0000",
                                sellRate : "1.0000",
                                updatedAt: "2026-06-25T10:00:00Z"
                        ],
                        [
                                currency : "USD",
                                buyRate  : "90.0000",
                                sellRate : "92.0000",
                                updatedAt: "2026-06-25T10:00:00Z"
                        ],
                        [
                                currency : "CNY",
                                buyRate  : "12.4000",
                                sellRate : "12.8000",
                                updatedAt: "2026-06-25T10:00:00Z"
                        ]
                ])
            }
        },
        Contract.make {
            name "update_exchange_rates"
            request {
                method PUT()
                url "/api/exchange/rates"
                headers {
                    contentType applicationJson()
                }
                body(
                        rates: [
                                [
                                        currency: "USD",
                                        buyRate : "91.0000",
                                        sellRate: "93.0000"
                                ]
                        ]
                )
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body([
                        [
                                currency : "RUB",
                                buyRate  : "1.0000",
                                sellRate : "1.0000",
                                updatedAt: "2026-06-25T10:00:00Z"
                        ],
                        [
                                currency : "USD",
                                buyRate  : "91.0000",
                                sellRate : "93.0000",
                                updatedAt: "2026-06-25T10:00:00Z"
                        ],
                        [
                                currency : "CNY",
                                buyRate  : "12.4000",
                                sellRate : "12.8000",
                                updatedAt: "2026-06-25T10:00:00Z"
                        ]
                ])
            }
        },
        Contract.make {
            name "convert_exchange_rate"
            request {
                method GET()
                urlPath("/api/exchange/conversion") {
                    queryParameters {
                        parameter "sourceCurrency": "USD"
                        parameter "targetCurrency": "CNY"
                        parameter "amount": "100.00"
                    }
                }
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body(
                        sourceCurrency: "USD",
                        targetCurrency: "CNY",
                        sourceAmount  : "100.00",
                        targetAmount  : "741.94",
                        rate          : "7.419355",
                        updatedAt     : "2026-06-25T10:00:00Z"
                )
            }
        }
]
