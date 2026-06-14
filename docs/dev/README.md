# Локальные секреты разработки

В этом учебном development-only репозитории нет `docs/infra/SECRETS.md`.

Для локального запуска секреты задаются через переменные окружения или локальный `.env`, который не должен попадать в git. Значения секретов в документации не раскрываются.

Используемые переменные:

- `CASH_SERVICE_CLIENT_SECRET` - client secret Keycloak-клиента `cash-service` для Client Credentials Flow.
- `TRANSFER_SERVICE_CLIENT_SECRET` - client secret Keycloak-клиента `transfer-service` для Client Credentials Flow.
