# Банк

Учебное микросервисное приложение для девятого спринта. Пользователь входит через HTML-интерфейс, редактирует профиль аккаунта, пополняет и снимает виртуальные деньги, переводит деньги другим пользователям.

## Архитектура

Приложение собрано как Gradle multi-module проект на Java 21 и Spring Boot.

- `front-ui` - HTML-интерфейс на Spring MVC и Thymeleaf.
- `bank-gateway` - Spring Cloud Gateway, внешняя точка входа `/api`.
- `accounts-service` - аккаунты, профиль пользователя, баланс и internal API баланса.
- `cash-service` - пользовательские операции пополнения и снятия.
- `transfer-service` - пользовательские переводы между аккаунтами.
- `notifications-service` - прием уведомлений и запись событий в лог.
- `shared` - общие вспомогательные классы без бизнес-логики.
- `discovery-server` - Eureka Server для service discovery.

Схема модулей:

```text
my-bank-app/
  front-ui/
  bank-gateway/
  accounts-service/
  cash-service/
  transfer-service/
  notifications-service/
  shared/
  discovery-server/
```

Поток пользовательского запроса:

1. Пользователь входит во `front-ui` через Keycloak.
2. `front-ui` вызывает backend только через `bank-gateway`.
3. `bank-gateway` проверяет JWT и пробрасывает его в пользовательские сервисы.
4. `cash-service` и `transfer-service` для межсервисных вызовов получают service JWT через Client Credentials Flow.
5. Сервисы регистрируются в Eureka.

Каждый сервис хранит собственную конфигурацию в `src/main/resources/application.yml`. Локальный запуск использует значения по умолчанию и переменные окружения, а Kubernetes переопределяет настройки через ConfigMap и Secret.

Межсервисные вызовы из `cash-service` и `transfer-service` идут через Eureka и Spring Cloud LoadBalancer: клиенты используют логические адреса `http://accounts-service` и `http://notifications-service`, а не фиксированные host:port.

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
- Gateway API: `http://localhost:8080`
- Keycloak: `http://localhost:8180`
- Eureka: `http://localhost:8761`
- PostgreSQL: `localhost:5432`, database `bank`, user `postgres`

Пользовательские endpoints публикуются через Gateway:

- `GET /api/accounts/me`
- `PUT /api/accounts/me`
- `GET /api/accounts/recipients`
- `POST /api/cash/deposit`
- `POST /api/cash/withdraw`
- `POST /api/transfers`

Internal endpoints `accounts-service` вида `/api/accounts/internal/...` не публикуются через Gateway.

## Запуск через Docker Compose

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
docker compose logs -f bank-gateway
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
docker compose up -d postgres keycloak discovery-server
```

После этого можно запускать приложения обычными Spring Boot run configurations. Удобный порядок:

1. `DiscoveryServerApplication`
2. `AccountsServiceApplication`
3. `NotificationsServiceApplication`
4. `CashServiceApplication`
5. `TransferServiceApplication`
6. `BankGatewayApplication`
7. `FrontUiApplication`

Если сервис запускается из IDE, оставь его порт свободным и не поднимай такой же сервис в Docker Compose.

## Запуск через Gradle

Тесты всех модулей:

```powershell
.\gradlew.bat --no-daemon --console=plain test
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

## Ручная проверка

Перед проверкой приложение должно быть поднято командой:

```powershell
docker compose up -d --wait
```

Сценарий:

1. Открыть `http://localhost:8085`.
2. Войти пользователем `ivan` / `ivan`.
3. Проверить баланс `1000.00 RUB`.
4. Пополнить счёт на `250.00`, ожидать баланс `1250.00`.
5. Снять `100.00`, ожидать баланс `1150.00`.
6. Перевести `150.00` пользователю `petr`, ожидать баланс `1000.00`.
7. Попробовать снять `999999.00`, ожидать ошибку недостатка средств.
8. Проверить, что в логах `notifications-service` появились уведомления по успешным операциям.

## Ограничения спринта

В рамках текущего спринта осознанно не реализуются без отдельного решения ревьюера или наставника:

- полноценный Circuit Breaker;
- Transactional Outbox;
- Kubernetes;
- Jenkins CI/CD;
- Kafka, JMS или отдельная шина данных;
- production-grade мониторинг, аудит и централизованная аналитика логов.

В клиентских модулях используется простая локальная защита от недоступности зависимых сервисов (`SimpleCircuitBreaker`) как учебное ограничение. Полноценный Circuit Breaker на базе отдельной библиотеки и Transactional Outbox не внедряются в этом спринте без отдельного подтверждения ревьюера или наставника.
