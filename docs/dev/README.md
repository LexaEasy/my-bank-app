# Локальные секреты разработки

Для локального запуска секреты задаются через переменные окружения или локальный `.env`, который не должен попадать в git. Значения секретов в документации не раскрываются.

Используемые переменные:

- `CASH_SERVICE_CLIENT_SECRET` - client secret Keycloak-клиента `cash-service` для Client Credentials Flow.
- `TRANSFER_SERVICE_CLIENT_SECRET` - client secret Keycloak-клиента `transfer-service` для Client Credentials Flow.
- `FRONT_UI_CLIENT_SECRET` - client secret Keycloak-клиента `front-ui` для Authorization Code Flow.
- `EXCHANGE_GENERATOR_CLIENT_SECRET` - client secret Keycloak-клиента `exchange-generator` для отправки курсов в `exchange-service`.
- `BANK_BLOCKER_BASE_URL` - базовый URL `blocker-service` для локальных переопределений; в Docker Compose используется service discovery.
- `BANK_EXCHANGE_BASE_URL` - базовый URL `exchange-service` для локальных переопределений; в Docker Compose используется service discovery.
- `BANK_BLOCKER_MAX_AMOUNT` - лимит суммы операции для локальной настройки `blocker-service`.
- `BANK_EXCHANGE_GENERATOR_FIXED_DELAY_MS` - интервал отправки курсов `exchange-generator` в миллисекундах.
