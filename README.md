# Банк

Учебное микросервисное приложение для девятого спринта. Пользователь входит через HTML-интерфейс, редактирует профиль аккаунта, пополняет и снимает виртуальные деньги, переводит деньги другим пользователям.

## Цель проекта

Реализовать приложение на Java 21 и Spring Boot с микросервисной архитектурой:

- пользовательский Front UI работает только после OAuth2-входа;
- Front UI обращается к backend только через Gateway;
- Gateway пробрасывает пользовательский JWT в пользовательские сервисы;
- межсервисные вызовы выполняются через Client Credentials Flow;
- сервисы регистрируются в Eureka;
- общая конфигурация читается из Spring Cloud Config Server;
- данные хранятся в PostgreSQL, отдельная схема на сервис;
- REST API проверяются тестами, включая Spring Cloud Contract для межсервисных контрактов.

## Планируемые модули

- `config-server` - Spring Cloud Config Server для локальных конфигураций.
- `discovery-server` - Eureka Server для service discovery.
- `bank-gateway` - Spring Cloud Gateway, внешняя точка входа `/api`.
- `accounts-service` - аккаунты, профиль пользователя, баланс, внутренние операции с балансом.
- `cash-service` - пользовательские операции пополнения и снятия.
- `transfer-service` - пользовательские переводы между аккаунтами.
- `notifications-service` - прием уведомлений и запись событий в лог.
- `front-ui` - HTML-интерфейс на Spring MVC и Thymeleaf.

Скелет фронта для будущего импорта находится вне репозитория: `C:\Projects\Y_Java\Sprint 9\my-bank-front-app`.

## Основные внешние маршруты

Пользовательские endpoints публикуются через Gateway с префиксом `/api`:

- `GET /api/accounts/me`
- `PUT /api/accounts/me`
- `GET /api/accounts/recipients`
- `POST /api/cash/deposit`
- `POST /api/cash/withdraw`
- `POST /api/transfers`

Internal endpoints `accounts-service` вида `/api/accounts/internal/...` не должны публиковаться во внешних маршрутах Gateway. Они предназначены только для прямых межсервисных вызовов с service JWT.

## Тестовые пользователи

Для локального Keycloak должны быть подготовлены пользователи:

| Username | Password | Roles |
| --- | --- | --- |
| `ivan` | `test123` | `USER`, `ACCOUNTS_READ`, `ACCOUNTS_WRITE`, `CASH_WRITE`, `TRANSFER_WRITE` |
| `petr` | `test123` | `USER`, `ACCOUNTS_READ`, `ACCOUNTS_WRITE`, `CASH_WRITE`, `TRANSFER_WRITE` |
| `anna` | `test123` | `USER`, `ACCOUNTS_READ`, `ACCOUNTS_WRITE`, `CASH_WRITE`, `TRANSFER_WRITE` |

## Начальные данные

| Login | Name | Birthdate | Balance | Currency |
| --- | --- | --- | --- | --- |
| `ivan` | `Иванов Иван` | `1990-01-15` | `1000.00` | `RUB` |
| `petr` | `Петров Пётр` | `1988-03-20` | `500.00` | `RUB` |
| `anna` | `Сидорова Анна` | `1995-07-10` | `750.00` | `RUB` |

## Локальная разработка

Команды будут актуализироваться по мере добавления модулей:

```powershell
.\gradlew projects
.\gradlew test
docker compose config
```

После полной сборки ожидаемый ручной сценарий:

1. Войти во Front UI пользователем `ivan` / `test123`.
2. Проверить баланс `1000.00 RUB`.
3. Пополнить счет на `250.00` и получить баланс `1250.00`.
4. Снять `100.00` и получить баланс `1150.00`.
5. Перевести `150.00` пользователю `petr` и получить баланс `1000.00`.
6. Попробовать снять `999999.00` и получить ошибку недостатка средств.

## Ограничения спринта

В рамках этого спринта не реализуются без отдельного решения ревьюера или наставника:

- полноценный Circuit Breaker;
- Transactional Outbox;
- Kubernetes;
- Jenkins CI/CD;
- Kafka, JMS или отдельная шина данных;
- production-grade мониторинг, аудит и централизованная аналитика логов.
