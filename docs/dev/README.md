# Локальные секреты разработки

Для локального запуска секреты задаются через переменные окружения или локальный `.env`, который не должен попадать в git. Значения секретов в документации не раскрываются.

## Переменные локального запуска

- `CASH_SERVICE_CLIENT_SECRET` - client secret Keycloak-клиента `cash-service` для Client Credentials Flow.
- `TRANSFER_SERVICE_CLIENT_SECRET` - client secret Keycloak-клиента `transfer-service` для Client Credentials Flow.
- `FRONT_UI_CLIENT_SECRET` - client secret Keycloak-клиента `front-ui` для Authorization Code Flow.
- `EXCHANGE_GENERATOR_CLIENT_SECRET` - client secret Keycloak-клиента `exchange-generator` для отправки курсов в `exchange-service`.
- `BANK_BLOCKER_BASE_URL` - базовый URL `blocker-service` для локальных переопределений; в Docker Compose используется service discovery.
- `BANK_EXCHANGE_BASE_URL` - базовый URL `exchange-service` для локальных переопределений; в Docker Compose используется service discovery.
- `BANK_BLOCKER_MAX_AMOUNT` - лимит суммы операции для локальной настройки `blocker-service`.
- `BANK_EXCHANGE_GENERATOR_FIXED_DELAY_MS` - интервал отправки курсов `exchange-generator` в миллисекундах.

## Kubernetes Secret

Перед установкой Helm-чарта в namespace должны существовать:

- `bank-service-credentials` с ключами `FRONT_UI_CLIENT_SECRET`, `CASH_SERVICE_CLIENT_SECRET`, `TRANSFER_SERVICE_CLIENT_SECRET`, `EXCHANGE_GENERATOR_CLIENT_SECRET`;
- `postgresql-credentials` с ключом `password`;
- `keycloak-credentials` с ключами `admin-username`, `admin-password`;
- `keycloak-realm` с ключом `bank-realm.json`.

Задайте значения только в текущей PowerShell-сессии:

```powershell
$env:FRONT_UI_CLIENT_SECRET = "<set-locally>"
$env:CASH_SERVICE_CLIENT_SECRET = "<set-locally>"
$env:TRANSFER_SERVICE_CLIENT_SECRET = "<set-locally>"
$env:EXCHANGE_GENERATOR_CLIENT_SECRET = "<set-locally>"
$env:POSTGRES_PASSWORD = "<set-locally>"
$env:KEYCLOAK_ADMIN_USERNAME = "<set-locally>"
$env:KEYCLOAK_ADMIN_PASSWORD = "<set-locally>"
```

Создайте или обновите Secret:

```powershell
kubectl create namespace dev --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic bank-service-credentials `
  --from-literal=FRONT_UI_CLIENT_SECRET=$env:FRONT_UI_CLIENT_SECRET `
  --from-literal=CASH_SERVICE_CLIENT_SECRET=$env:CASH_SERVICE_CLIENT_SECRET `
  --from-literal=TRANSFER_SERVICE_CLIENT_SECRET=$env:TRANSFER_SERVICE_CLIENT_SECRET `
  --from-literal=EXCHANGE_GENERATOR_CLIENT_SECRET=$env:EXCHANGE_GENERATOR_CLIENT_SECRET `
  --namespace dev --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic postgresql-credentials `
  --from-literal=password=$env:POSTGRES_PASSWORD `
  --namespace dev --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic keycloak-credentials `
  --from-literal=admin-username=$env:KEYCLOAK_ADMIN_USERNAME `
  --from-literal=admin-password=$env:KEYCLOAK_ADMIN_PASSWORD `
  --namespace dev --dry-run=client -o yaml | kubectl apply -f -
```

`keycloak-realm` создаётся из подготовленного вне Git файла:

```powershell
kubectl create secret generic keycloak-realm `
  --from-file=bank-realm.json=C:\secure\bank-realm.json `
  --namespace dev --dry-run=client -o yaml | kubectl apply -f -
```

Не используйте `keycloak/realms/bank-realm.json` без подготовки: текущий файл содержит placeholders и тестовые credentials. Безопасная подготовка realm выполняется на шаге развёртывания OAuth 2.0 сервера.

После создания Secret очистите переменные текущей сессии:

```powershell
Remove-Item Env:FRONT_UI_CLIENT_SECRET
Remove-Item Env:CASH_SERVICE_CLIENT_SECRET
Remove-Item Env:TRANSFER_SERVICE_CLIENT_SECRET
Remove-Item Env:EXCHANGE_GENERATOR_CLIENT_SECRET
Remove-Item Env:POSTGRES_PASSWORD
Remove-Item Env:KEYCLOAK_ADMIN_USERNAME
Remove-Item Env:KEYCLOAK_ADMIN_PASSWORD
```
