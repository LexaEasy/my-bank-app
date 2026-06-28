# Helm-чарты приложения

Каталог `helm/charts` содержит базовые сабчарты Java-компонентов приложения:

- `front-ui` - модуль `front-ui`;
- `accounts-service` - модуль `accounts-service`;
- `cash-service` - модуль `cash-service`;
- `transfer-service` - модуль `transfer-service`;
- `exchange-service` - модуль `exchange-service`;
- `exchange-generator` - модуль `exchange-generator`;
- `blocker-service` - модуль `blocker-service`;
- `notifications-service` - модуль `notifications-service`;
- `bank-gateway` - модуль `bank-gateway`, временно нужен до замены маршрутизации Kubernetes Gateway API или Ingress.

Значения секретов в `values.yaml` не хранятся. Секреты должны передаваться через Kubernetes `Secret` или внешний механизм секретов на следующих шагах настройки развёртывания.
