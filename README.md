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

## Kafka и уведомления

Kafka используется как асинхронная граница между банковскими сервисами и `notifications-service`. `accounts-service`, `cash-service` и `transfer-service` публикуют события после успешных операций, а `notifications-service` читает их и записывает уведомления в application log. REST-вызовов Notifications и OAuth-клиента Notifications нет.

Основные параметры:

- основной топик — `bank.notifications`, 3 partition;
- consumer group — `bank-notifications`;
- dead letter topic — `bank.notifications.dlt`, 3 partition, retention 7 дней;
- message key — `recipientLogin`, поэтому события одного получателя сохраняют порядок внутри partition;
- Kafka работает в single-node combined KRaft-режиме без ZooKeeper;
- автоматическое создание топиков отключено, топики объявляются через Spring `KafkaAdmin`.

При временной ошибке consumer выполняет ровно три попытки: первая обработка и два повтора с интервалом 1 секунда (`FixedBackOff(1000L, 2L)`). После исчерпания попыток событие отправляется в DLT. Невалидный JSON и ошибки валидации отправляются в DLT без повторов.

Offset исходного события фиксируется только после успешной обработки либо успешной публикации в DLT. Если публикация в DLT завершилась ошибкой, offset не фиксируется. Поэтому consumer реализует обработку **at least once**: событие может быть обработано повторно после сбоя, а consumer должен безопасно принимать дубликаты.

Бизнес-транзакция и Kafka send не являются одной атомарной операцией. Сначала фиксируется изменение банковских данных, затем отправляется событие. Окончательная ошибка producer:

- не откатывает успешную банковскую операцию;
- не заменяет успешный HTTP-ответ ошибкой Kafka;
- регистрируется обработчиком ошибки;
- может привести к потере уведомления, поскольку Transactional Outbox намеренно не реализован.

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

| Username | Roles |
| --- | --- |
| `ivan` | `USER`, `ACCOUNTS_READ`, `ACCOUNTS_WRITE`, `CASH_WRITE`, `TRANSFER_WRITE` |
| `petr` | `USER`, `ACCOUNTS_READ`, `ACCOUNTS_WRITE`, `CASH_WRITE`, `TRANSFER_WRITE` |
| `anna` | `USER`, `ACCOUNTS_READ`, `ACCOUNTS_WRITE`, `CASH_WRITE`, `TRANSFER_WRITE` |

Пароли демонстрационных пользователей хранятся только в локальном
SOPS-зашифрованном файле. Подготовка realm описана в локальном runbook
`docs/dev/README.md`, который не отслеживается Git.

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

### Локальные секреты

Репозиторий является development-only и не содержит
`docs/infra/SECRETS.md`. Источники истины для локального secret workflow:
`.sops.yaml`, `envs/dev/secrets/*.enc.yaml` и локальный игнорируемый runbook
`docs/dev/README.md`.

Закрытый age-ключ хранится вне Git в `%APPDATA%\sops\age\keys.txt`. Перед
запуском расшифруйте Keycloak realm в игнорируемый runtime-каталог:

```powershell
New-Item -ItemType Directory -Force envs/dev/runtime | Out-Null

sops --decrypt `
  --input-type yaml `
  --output-type json `
  --output envs/dev/runtime/bank-realm.json `
  envs/dev/secrets/keycloak-realm.enc.yaml
```

Пароли и содержимое расшифрованного realm нельзя выводить в логи, сохранять в
README или добавлять в Git. После остановки окружения удалите runtime-файл:

```powershell
Remove-Item -LiteralPath envs/dev/runtime/bank-realm.json
```

Сначала собрать executable JAR:

```powershell
.\gradlew.bat --no-daemon --console=plain clean bootJar
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

Healthcheck определены в Docker Compose только для HTTP-сервисов, которым они
нужны для локального порядка запуска. Kafka и non-web
`notifications-service` не имеют добавленных Sprint 11 healthcheck.
Dockerfile не гарантирует наличие встроенного `HEALTHCHECK`.

Посмотреть логи конкретного сервиса:

```powershell
docker compose logs -f front-ui
docker compose logs -f accounts-service
docker compose logs -f kafka
docker compose logs -f notifications-service
```

Посмотреть топики, consumer group и содержимое DLT:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group bank-notifications --describe
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic bank.notifications.dlt --from-beginning
```

Проверка persistence Kafka:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic bank.notifications
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group bank-notifications --describe
docker compose restart kafka
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic bank.notifications
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group bank-notifications --describe
```

До и после перезапуска сравниваются `TopicId` и текущие offsets каждой
partition. Конкретные значения зависят от запуска и в документации не
фиксируются. Топик и offsets должны сохраниться в именованном volume
`kafka-data`.

Не используйте `docker compose down --volumes` в recovery/persistence
сценариях: команда намеренно удаляет постоянные данные.

Остановить контейнеры без удаления образов:

```powershell
docker compose down
```

## Запуск из IDE

Для запуска из IDE сначала подними платформенные сервисы:

```powershell
docker compose up -d postgres keycloak kafka
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

Unit- и integration-тесты всех модулей, включая `@EmbeddedKafka` в KRaft-режиме:

```powershell
.\gradlew.bat --no-daemon --console=plain test
```

Контрактные тесты сервисов:

```powershell
.\gradlew.bat --no-daemon --console=plain :accounts-service:contractTest :cash-service:contractTest :transfer-service:contractTest :exchange-service:contractTest :blocker-service:contractTest :notifications-service:contractTest
```

Полная команда, используемая umbrella CI:

```powershell
.\gradlew.bat --no-daemon --console=plain test contractTest
```

Integration-тесты Kafka не требуют Docker или Testcontainers: broker запускается внутри JVM через Spring Kafka Test.

Сборка JAR всех приложений:

```powershell
.\gradlew.bat --no-daemon --console=plain clean bootJar
```

Запуск одного приложения:

```powershell
.\gradlew.bat --no-daemon --console=plain :front-ui:bootRun
.\gradlew.bat --no-daemon --console=plain :bank-gateway:bootRun
```

## Сборка Docker images

Перед сборкой образов нужно собрать executable JAR:

```powershell
.\gradlew.bat --no-daemon --console=plain clean bootJar
```

Сборка всех образов через Docker Compose:

```powershell
docker compose build
```

Сборка отдельного образа:

```powershell
docker build -t my-bank-front-ui:local front-ui
docker build -t my-bank-accounts-service:local accounts-service
```

Каждый Spring Boot модуль создаёт один исполняемый артефакт с именем
`<module>.jar`. Plain JAR отключён, а Dockerfile копирует точный путь к
артефакту и завершает сборку ошибкой при его отсутствии.

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
- `keycloak-realm` с ключом `bank-realm.json`, созданный из локально
  расшифрованного SOPS-файла.

Значения секретов не хранятся в Git. Создание или обновление realm Secret:

```powershell
kubectl create secret generic keycloak-realm `
  --from-file=bank-realm.json=envs/dev/runtime/bank-realm.json `
  --namespace dev `
  --dry-run=client -o yaml | kubectl apply -f -
```

Helm использует существующий Secret и не рендерит credentials в manifests.

Проверка и установка в `dev` namespace:

```powershell
helm dependency update helm/bank
helm lint helm/bank -f helm/bank/values-dev.yaml
helm template bank helm/bank --namespace dev -f helm/bank/values-dev.yaml
helm upgrade --install bank helm/bank --namespace dev --create-namespace -f helm/bank/values-dev.yaml --rollback-on-failure --timeout 5m
```

Kafka разворачивается локальным Helm-сабчартом `helm/charts/kafka` как StatefulSet с PVC. Значение `kafka.clusterId` постоянно для каждого окружения и не должно меняться при повторной установке поверх существующего PVC.

Просмотр состояния и логов:

```powershell
kubectl get statefulset,pod,pvc -n dev -l app.kubernetes.io/name=kafka
kubectl logs -n dev statefulset/kafka -f
kubectl logs -n dev deployment/notifications-service -f
```

Проверка persistence в Kubernetes:

```powershell
kubectl get pvc -n dev kafka-data
kubectl exec -n dev kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic bank.notifications
kubectl exec -n dev kafka-0 -- /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group bank-notifications --describe
kubectl delete pod -n dev kafka-0
kubectl wait --for=condition=Ready pod/kafka-0 -n dev --timeout=180s
kubectl exec -n dev kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic bank.notifications
kubectl exec -n dev kafka-0 -- /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group bank-notifications --describe
```

До и после пересоздания pod сравниваются `TopicId`, offsets и lag. PVC
`kafka-data` должен оставаться `Bound`; одноразовые значения конкретного
запуска в README не сохраняются.

Проверка `at least once`: выполнить успешную банковскую операцию, дождаться обработки события, перезапустить `notifications-service` и убедиться, что подтверждённая запись не обработана повторно. Затем остановить Notifications, выполнить ещё одну операцию, запустить Notifications и убедиться, что накопленное событие обработано. При аварии до фиксации offset допустима повторная доставка одного события.

Helm smoke tests:

```powershell
helm test bank --namespace dev
helm test bank --namespace dev --logs
```

Test hooks сохраняют завершённые pods до следующего запуска, поэтому
`--logs` может вывести результаты broker, topic и persistence-проверок и
должен завершиться с кодом `0`. Следующий запуск удаляет предыдущие test
resources политикой `before-hook-creation`.

Dry-run для test/prod окружений:

```powershell
helm upgrade --install bank helm/bank --namespace test --create-namespace -f helm/bank/values-test.yaml --set global.imageRegistry=registry.example.com/my-bank --set global.imageTag=ci-test --rollback-on-failure --timeout 5m --dry-run=client
helm upgrade --install bank helm/bank --namespace prod --create-namespace -f helm/bank/values-prod.yaml --set global.imageRegistry=registry.example.com/my-bank --set global.imageTag=ci-prod --rollback-on-failure --timeout 5m --dry-run=client
```

## Конфигурация Kafka topology

Kafka topology для событий уведомлений управляется из одной модели конфигурации:

- Java-код привязывает `bank.kafka.*` к `NotificationTopicsProperties`;
- `NotificationTopicsConfiguration` использует те же properties для объявлений `NewTopic` через `KafkaAdmin`;
- Helm хранит общие env-переменные topology уведомлений в `global.env`, поэтому producer-сервисы и `notifications-service` получают одинаковые значения;
- Helm tests читают rendered env values и сравнивают фактические partitions, replication factor и DLT retention топиков с ними.

Значения по умолчанию:

- `BANK_KAFKA_NOTIFICATIONS_PARTITIONS`: `3`;
- `BANK_KAFKA_NOTIFICATIONS_DLT_PARTITIONS`: `3`;
- `BANK_KAFKA_NOTIFICATIONS_REPLICATION_FACTOR`: `1`;
- `BANK_KAFKA_NOTIFICATIONS_DLT_RETENTION_MS`: `604800000`.

`BANK_KAFKA_NOTIFICATIONS_DLT_PARTITIONS` должен быть больше или равен `BANK_KAFKA_NOTIFICATIONS_PARTITIONS`. Некорректная topology отклоняется при старте Spring и при Helm render.

## Диагностика ошибок публикации Kafka

Accounts, Cash и Transfer сохраняют best-effort модель публикации уведомлений.
Банковская операция и отправка Kafka-события не образуют общую транзакцию:
успешно зафиксированная операция остаётся успешной даже при окончательной
ошибке producer. Это означает dual-write gap — уведомление может быть потеряно
между фиксацией данных в PostgreSQL и успешной публикацией в Kafka.

Окончательная ошибка публикации:

- записывается в `ERROR` с `eventId`, `operationId`, source, topic и
  низкокардинальной категорией ошибки;
- увеличивает counter `bank.kafka.publication.failures`;
- не изменяет HTTP-результат банковской операции;
- не запускает дополнительный in-memory или durable retry.

Метрика экспортируется как
`bank_kafka_publication_failures_total`. Допустимые теги: `source`, `topic`,
`error_category` и общий тег `application`. Идентификаторы операций,
пользовательские данные, JWT, credentials и тексты исключений в теги не
добавляются.

Порядок диагностики:

1. Проверить сработавший alert `KafkaNotificationPublicationFailures`.
2. Определить сервис, topic и категорию по метке серии Prometheus.
3. Найти `ERROR` соответствующего сервиса по времени, `eventId` или
   `operationId`.
4. Проверить доступность Kafka, состояние producer и конфигурацию topic.
5. Проверить, была ли банковская операция успешно сохранена.
6. При необходимости восстановить утраченное уведомление отдельной
   согласованной процедурой, не повторяя денежную операцию.

Prometheus endpoint доступен только через внутренний management Service:

- `accounts-service-management:8091/actuator/prometheus`;
- `cash-service-management:8092/actuator/prometheus`;
- `transfer-service-management:8093/actuator/prometheus`.

Gateway не публикует management endpoints. Helm создаёт для producer-сервисов
внутренние `Service`, `ServiceMonitor` и `PrometheusRule`.

Transactional Outbox, CDC и durable retry остаются отдельным архитектурным
backlog и не входят в Sprint 11.

## Jenkins CI/CD

В репозитории есть root `Jenkinsfile` для umbrella pipeline и Jenkinsfile рядом с каждым микросервисом. Pipeline покрывает validate, unit/integration/contract tests, `clean bootJar` и Helm lint/template. Docker build, image push и deploy отключены по умолчанию и выполняются только при явном включении соответствующих параметров.

Ожидаемые Jenkins credentials:

- `bank-registry-credentials` - username/password для container registry;
- `bank-kubeconfig` - kubeconfig file credential для доступа к Kubernetes.

Параметры root pipeline:

- `IMAGE_REGISTRY` - registry namespace, например `registry.example.com/my-bank`;
- `IMAGE_TAG` - тег образов, пустое значение использует `BUILD_NUMBER`;
- `BUILD_IMAGES` - собрать Docker images;
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
2. Получить пароль пользователя `ivan` через локальный SOPS-контур и выполнить вход.
3. Проверить баланс `1000.00 RUB` и таблицу курсов валют.
4. Пополнить счёт на `250.00`, ожидать баланс `1250.00`.
5. Снять `100.00`, ожидать баланс `1150.00`.
6. Перевести `150.00` пользователю `petr`, ожидать баланс `1000.00`.
7. Попробовать снять `999999.00`, ожидать ошибку недостатка средств.
8. Проверить, что в логах `notifications-service` появились уведомления по успешным операциям.
9. Остановить `notifications-service`, выполнить успешную операцию и проверить, что операция не зависит от доступности consumer.
10. Запустить `notifications-service`, проверить обработку накопленного события и возврат lag к `0`.
11. Перезапустить Kafka без удаления volume/PVC и повторить успешную операцию.
12. Сравнить `TopicId` и offsets до и после перезапуска.

## Ограничения спринта

В рамках Sprint 11 намеренно не реализуются:

- ELK или другое централизованное хранение и анализ логов;
- полный production-grade monitoring stack и Grafana;
- аудит и distributed tracing;
- Kafka Exactly Once Semantics и сквозная гарантия между PostgreSQL commit и Kafka send;
- глобальный порядок сообщений и multi-node Kafka;
- ZooKeeper, внешний Gateway/Ingress для Kafka и REST fallback Notifications;
- OAuth Client Credentials Flow только ради Notifications;
- Transactional Outbox, CDC, Schema Registry и in-memory background retry;
- Testcontainers для Kafka integration tests;
- создание топиков через Helm hook или только ручной командой;
- переработка бизнес-правил валют, blocker и балансовых операций;
- удаление REST-вызовов, не относящихся к Notifications;
- хранение credentials в Compose, Helm values, Java config или README;
- изменение истории Git и push без явного подтверждения.

Технические health endpoints, Kubernetes probes, Micrometer counter,
внутренние management Service, ServiceMonitor и PrometheusRule добавлены как
узкие исключения для закрытия требований Sprint 11. Они не означают
production readiness. В клиентских модулях остаётся учебная локальная защита
`SimpleCircuitBreaker`; полноценный Circuit Breaker и Transactional Outbox
требуют отдельного решения.
