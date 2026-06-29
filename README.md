# Банк

Учебное микросервисное приложение. Пользователь входит через HTML-интерфейс, редактирует профиль аккаунта, пополняет и снимает виртуальные деньги, переводит деньги другим пользователям и видит курсы валют.

## Архитектура

Приложение собрано как Gradle multi-module проект на Java 21 и Spring Boot.

- `front-ui` - HTML-интерфейс на Spring MVC и Thymeleaf.
- `bank-gateway` - Spring Cloud Gateway, сохранён как совместимый модуль, но не является обязательной runtime-точкой в Kubernetes.
- `accounts-service` - аккаунты, профиль пользователя, баланс и internal API баланса.
- `cash-service` - пользовательские операции пополнения и снятия.
- `transfer-service` - пользовательские переводы между аккаунтами.
- `exchange-service` - хранение и выдача курсов валют.
- `exchange-generator` - периодическое обновление курсов валют.
- `blocker-service` - проверка подозрительных денежных операций.
- `notifications-service` - прием уведомлений и запись событий в лог.
- `shared` - общие вспомогательные классы без бизнес-логики.

Схема модулей:

```text
my-bank-app/
  front-ui/
  accounts-service/
  cash-service/
  transfer-service/
  exchange-service/
  exchange-generator/
  blocker-service/
  notifications-service/
  bank-gateway/
  shared/
```

Поток пользовательского запроса:

1. Пользователь входит во `front-ui` через Keycloak.
2. `front-ui` вызывает backend-сервисы через Kubernetes Service/DNS или локальные service names.
3. Gateway API публикует внешние пользовательские endpoints и не публикует internal API.
4. `cash-service` и `transfer-service` для межсервисных вызовов получают service JWT через Client Credentials Flow.
5. В Kubernetes service discovery выполняется через `Service` и DNS, без Eureka.

Каждый сервис хранит собственную конфигурацию в `src/main/resources/application.yml`. Локальный запуск использует значения по умолчанию и переменные окружения, а Kubernetes переопределяет настройки через ConfigMap и Secret.

Межсервисные вызовы используют явные base URL вида `http://accounts-service:8081`, которые в Kubernetes разрешаются через `Service` и DNS.

Контрактные проверки лежат рядом с сервисами-поставщиками и потребителями в `src/contractTest`. Internal API `accounts-service` используется только межсервисно и не должен публиковаться через Gateway.

## Данные

PostgreSQL используется как единая локальная инсталляция с отдельной схемой для данных аккаунтов:

- `accounts_schema` - таблицы `accounts-service`;
- `cash_schema` и `transfer_schema` не создаются, пока у сервисов нет собственных таблиц;
- `notifications_schema` не создаётся, потому что уведомления пишутся только в application log.

Для защиты конкурентных изменений баланса используется optimistic locking через поле `@Version` в entity аккаунта.

Балансовые операции защищены от повторного выполнения через `operationId`. `accounts-service` сохраняет начало операции в таблицу `processed_operations`, после успешного изменения баланса записывает JSON-ответ и при повторе того же запроса возвращает сохранённый результат без повторного изменения баланса. Если операция завершилась ошибкой, её статус сохраняется как `FAILED`, чтобы повтор с тем же `operationId` не запускал бизнес-операцию заново без явного нового идентификатора.

Бизнес-ошибки балансовых операций возвращают `422 Unprocessable Entity`: например, недостаток средств и перевод самому себе. Конфликты конкурентного изменения и конфликты идемпотентности остаются `409 Conflict`.

## Пользователи

Realm Keycloak: `bank-realm`.

| Username | Password | Roles |
| --- | --- | --- |
| `ivan` | `ivan` | `USER`, `ACCOUNTS_READ`, `ACCOUNTS_WRITE`, `CASH_WRITE`, `TRANSFER_WRITE` |
| `petr` | `petr` | `USER`, `ACCOUNTS_READ`, `ACCOUNTS_WRITE`, `CASH_WRITE`, `TRANSFER_WRITE` |
| `anna` | `anna` | `USER`, `ACCOUNTS_READ`, `ACCOUNTS_WRITE`, `CASH_WRITE`, `TRANSFER_WRITE` |

Начальные аккаунты:

| Login | Name | Birthdate | Balance | Currency |
| --- | --- | --- | --- | --- |
| `ivan` | `Иванов Иван` | `1990-01-15` | `1000.00` | `RUB` |
| `petr` | `Петров Пётр` | `1988-03-20` | `500.00` | `RUB` |
| `anna` | `Сидорова Анна` | `1995-07-10` | `750.00` | `RUB` |

## Основные URL

- Front UI: `http://localhost:8085`
- Gateway API: `http://localhost`
- Keycloak: `http://localhost:8180`
- PostgreSQL: `localhost:5432`, database `bank`, user `postgres`

Пользовательские endpoints публикуются через Gateway:

- `GET /api/accounts/me`
- `PUT /api/accounts/me`
- `GET /api/accounts/recipients`
- `POST /api/cash/deposit`
- `POST /api/cash/withdraw`
- `POST /api/transfers`
- `GET /api/exchange/rates`
- `GET /api/exchange/conversion`

Internal endpoints `accounts-service` вида `/api/accounts/internal/...` не публикуются через Gateway.

## Запуск через Docker Compose

Docker Compose сохранён только как локальный dev-сценарий. Обязательный способ развёртывания Sprint 10 - Kubernetes через Helm.

Сначала собрать executable JAR:

```powershell
.\gradlew.bat --no-daemon --console=plain bootJar
```

Проверить compose-файл:

```powershell
docker compose config
```

Собрать образы:

```powershell
docker compose build
```

Запустить приложение и дождаться healthcheck:

```powershell
docker compose up -d --wait
```

Проверить контейнеры:

```powershell
docker compose ps
```

Dockerfile каждого приложения содержит встроенный `HEALTHCHECK`, который проверяет `/actuator/health`. Поэтому health status доступен не только в Docker Compose, но и при запуске отдельного образа через `docker run`.

Посмотреть логи конкретного сервиса:

```powershell
docker compose logs -f front-ui
docker compose logs -f accounts-service
```

Остановить контейнеры без удаления образов:

```powershell
docker compose down
```

Сбросить локальные данные PostgreSQL и Keycloak:

```powershell
docker compose down --volumes
```

## Запуск из IDE

Для запуска из IDE сначала подними платформенные сервисы:

```powershell
docker compose up -d postgres keycloak
```

После этого можно запускать приложения обычными Spring Boot run configurations. Удобный порядок:

1. `AccountsServiceApplication`
2. `NotificationsServiceApplication`
3. `ExchangeServiceApplication`
4. `BlockerServiceApplication`
5. `CashServiceApplication`
6. `TransferServiceApplication`
7. `ExchangeGeneratorApplication`
8. `FrontUiApplication`

Если сервис запускается из IDE, оставь его порт свободным и не поднимай такой же сервис в Docker Compose.

## Запуск через Gradle

Тесты всех модулей:

```powershell
.\gradlew.bat --no-daemon --console=plain test
```

Контрактные тесты сервисов:

```powershell
.\gradlew.bat --no-daemon --console=plain :accounts-service:contractTest :cash-service:contractTest :transfer-service:contractTest :exchange-service:contractTest :blocker-service:contractTest :notifications-service:contractTest
```

Сборка JAR всех приложений:

```powershell
.\gradlew.bat --no-daemon --console=plain bootJar
```

Запуск одного приложения:

```powershell
.\gradlew.bat --no-daemon --console=plain :front-ui:bootRun
.\gradlew.bat --no-daemon --console=plain :bank-gateway:bootRun
```

## Сборка Docker images

Перед сборкой образов нужно собрать executable JAR:

```powershell
.\gradlew.bat --no-daemon --console=plain bootJar
```

Сборка всех образов через Docker Compose:

```powershell
docker compose build
```

Сборка отдельного образа:

```powershell
docker build --build-arg JAR_FILE=build/libs/*.jar -t my-bank-front-ui:local front-ui
docker build --build-arg JAR_FILE=build/libs/*.jar -t my-bank-accounts-service:local accounts-service
```

## Запуск через Helm

Для проверки Gateway API в локальном Docker Desktop-кластере заранее должны быть установлены Gateway API CRD и совместимый контроллер, создающий `GatewayClass nginx`.

Команды, которые использовались для локальной проверки:

```powershell
kubectl apply --server-side=true -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.5.1/standard-install.yaml
helm upgrade --install ngf oci://ghcr.io/nginx/charts/nginx-gateway-fabric --version 2.6.6 --namespace nginx-gateway --create-namespace
kubectl get gatewayclass nginx
```

Перед установкой chart в namespace должны существовать Kubernetes Secret:

- `bank-service-credentials` с ключами `FRONT_UI_CLIENT_SECRET`, `CASH_SERVICE_CLIENT_SECRET`, `TRANSFER_SERVICE_CLIENT_SECRET`, `EXCHANGE_GENERATOR_CLIENT_SECRET`;
- `postgresql-credentials` с ключом `password`;
- `keycloak-credentials` с ключами `admin-username`, `admin-password`.

Значения секретов не хранятся в git. Realm Keycloak создаётся Helm-чартом из `helm/charts/keycloak/files/bank-realm.json`; client secrets в realm подставляются из env-переменных Keycloak, которые берутся из `bank-service-credentials`.

Проверка и установка в `dev` namespace:

```powershell
helm dependency update helm/bank
helm lint helm/bank
helm template bank helm/bank --namespace dev -f helm/bank/values-dev.yaml
helm upgrade --install bank helm/bank --namespace dev --create-namespace -f helm/bank/values-dev.yaml --rollback-on-failure --timeout 5m
```

Helm smoke tests:

```powershell
helm test bank --namespace dev
```

Dry-run для test/prod окружений:

```powershell
helm upgrade --install bank helm/bank --namespace test --create-namespace -f helm/bank/values-test.yaml --set global.imageRegistry=registry.example.com/my-bank --set global.imageTag=ci-test --rollback-on-failure --timeout 5m --dry-run=client
helm upgrade --install bank helm/bank --namespace prod --create-namespace -f helm/bank/values-prod.yaml --set global.imageRegistry=registry.example.com/my-bank --set global.imageTag=ci-prod --rollback-on-failure --timeout 5m --dry-run=client
```

## Jenkins CI/CD

В репозитории есть root `Jenkinsfile` для umbrella pipeline и Jenkinsfile рядом с каждым микросервисом. Pipeline покрывает validate, test, `bootJar`, Docker build, image push, Helm lint/template, deploy в `test`, ручное подтверждение и deploy в `prod`.

Ожидаемые Jenkins credentials:

- `bank-registry-credentials` - username/password для container registry;
- `bank-kubeconfig` - kubeconfig file credential для доступа к Kubernetes.

Параметры root pipeline:

- `IMAGE_REGISTRY` - registry namespace, например `registry.example.com/my-bank`;
- `IMAGE_TAG` - тег образов, пустое значение использует `BUILD_NUMBER`;
- `PUSH_IMAGES` - отправлять собранные образы в registry;
- `DEPLOY_TEST` - выполнить deploy umbrella chart в namespace `test`;
- `DEPLOY_PROD` - после ручного подтверждения выполнить deploy в namespace `prod`.

Параметры сервисных pipeline аналогичны, но управляют одним образом и одним service chart. Значения registry credentials, kubeconfig и Kubernetes Secret не хранятся в Jenkinsfile.

## Ручная проверка

Перед проверкой приложение должно быть поднято командой:

```powershell
docker compose up -d --wait
```

Сценарий:

1. Открыть `http://localhost:8085`.
2. Войти пользователем `ivan` / `ivan`.
3. Проверить баланс `1000.00 RUB` и таблицу курсов валют.
4. Пополнить счёт на `250.00`, ожидать баланс `1250.00`.
5. Снять `100.00`, ожидать баланс `1150.00`.
6. Перевести `150.00` пользователю `petr`, ожидать баланс `1000.00`.
7. Попробовать снять `999999.00`, ожидать ошибку недостатка средств.
8. Проверить, что в логах `notifications-service` появились уведомления по успешным операциям.

## Ограничения спринта

В рамках текущего спринта осознанно не реализуются без отдельного решения ревьюера или наставника:

- полноценный Circuit Breaker;
- Transactional Outbox;
- production-grade Kubernetes-эксплуатация;
- Kafka, JMS или отдельная шина данных;
- production-grade мониторинг, аудит и централизованная аналитика логов.

В клиентских модулях используется простая локальная защита от недоступности зависимых сервисов (`SimpleCircuitBreaker`) как учебное ограничение. Полноценный Circuit Breaker на базе отдельной библиотеки и Transactional Outbox не внедряются в этом спринте без отдельного подтверждения ревьюера или наставника.
