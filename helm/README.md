# Helm-чарты приложения

Каталог `helm/charts` содержит три чарта:

- `spring-service` - общий chart для всех Spring Boot-сервисов;
- `keycloak` - специализированный chart сервера авторизации;
- `postgresql` - специализированный chart базы данных.

Общий `spring-service` создаёт `Deployment`, `Service`, `ServiceAccount`,
`ConfigMap` и Helm smoke-тест. Название приложения, образ, порт и остальные
настройки передаются через values.

Значения для самостоятельных релизов Spring-сервисов находятся в
`helm/values/services`. Например:

```powershell
helm upgrade --install accounts-service helm/charts/spring-service `
  --namespace test `
  -f helm/values/services/accounts-service.yaml
```

Зонтичный chart `helm/bank` подключает `spring-service` несколько раз через
Helm dependency aliases. Настройки каждого компонента находятся в отдельной
секции `helm/bank/values.yaml`, например `front-ui`, `accounts-service` или
`exchange-service`.

Значения секретов в `values.yaml` не хранятся. Секреты должны передаваться через Kubernetes `Secret` или внешний механизм секретов на следующих шагах настройки развёртывания.
