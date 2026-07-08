# Проверка Sprint 12

Файл заполняется только фактическими результатами после выполнения команд и
browser-сценариев. Credentials, полные логи, персональные данные, screenshots
с такими данными и одноразовые trace IDs не сохраняются.

## Окружение

- Дата:
- ОС и Kubernetes:
- Namespace: `dev`
- Версия commit:

## Проверки

| Проверка | Команда или URL | Ожидаемый результат | Фактический результат |
| --- | --- | --- | --- |
| Pods и PVC | `kubectl get pods,pvc -n dev` | Все pods Ready, PVC Bound | Не выполнено |
| Helm tests | `helm test bank -n dev --logs` | Все hooks успешны | Не выполнено |
| Prometheus targets | `http://localhost:9090/targets` | 9 targets UP | Не выполнено |
| Prometheus rules | `http://localhost:9090/rules` | 5 bank alerts | Не выполнено |
| Grafana dashboards | `http://localhost:3000` | 3 dashboard | Не выполнено |
| Zipkin traces | `http://localhost:9411` | Сквозные traces | Не выполнено |
| Kibana data view | `http://localhost:5601` | `bank-logs` существует | Не выполнено |

## Browser smoke

- OAuth2 login:
- Профиль и курсы:
- Deposit/withdraw/transfer:
- Бизнес-ошибки:
- Регрессии Sprint 11:

## Известные учебные ограничения

- Zipkin использует in-memory storage.
- Elasticsearch single-node и security отключены только для `dev`.
- Интерфейсы observability не публикуются через Gateway.
