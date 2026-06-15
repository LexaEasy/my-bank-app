import org.springframework.cloud.contract.spec.Contract

[
        Contract.make {
            name "get_current_account"
            request {
                method GET()
                url "/api/accounts/me"
                headers {
                    header "Authorization", "Bearer token"
                }
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body(
                        login: "ivan",
                        name: "Иванов Иван",
                        birthdate: "1990-01-15",
                        balance: "1000.00",
                        currency: "RUB"
                )
            }
        },
        Contract.make {
            name "update_current_account"
            request {
                method PUT()
                url "/api/accounts/me"
                headers {
                    contentType applicationJson()
                    header "Authorization", "Bearer token"
                }
                body(
                        name: "Иван Иванов",
                        birthdate: "1992-05-10"
                )
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body(
                        login: "ivan",
                        name: "Иван Иванов",
                        birthdate: "1992-05-10",
                        balance: "1000.00",
                        currency: "RUB"
                )
            }
        },
        Contract.make {
            name "get_recipients"
            request {
                method GET()
                url "/api/accounts/recipients"
                headers {
                    header "Authorization", "Bearer token"
                }
            }
            response {
                status OK()
                headers {
                    contentType applicationJson()
                }
                body([
                        [
                                login: "petr",
                                name : "Петров Пётр"
                        ],
                        [
                                login: "anna",
                                name : "Сидорова Анна"
                        ]
                ])
            }
        }
]
