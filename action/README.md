# Star Guard — проверка зависимостей в pull request

GitHub Action находит зависимости, которые добавляет PR (`package.json`,
`requirements*.txt`), ищет их репозитории на GitHub (через npm registry и PyPI)
и пишет в PR один комментарий с оценкой Star Guard. Комментарий обновляется
при новых коммитах.

Зачем: по данным исследования StarScout (ICSE 2026) большая часть фейковых звёзд
продвигает короткоживущие вредоносные репозитории — накрутка звёзд у новой
зависимости — повод посмотреть на неё внимательнее.

## Подключение

```yaml
# .github/workflows/star-guard.yml
name: Star Guard
on:
  pull_request:
    paths: ['**/package.json', '**/requirements*.txt']
permissions:
  contents: read
  pull-requests: write
jobs:
  deps:
    runs-on: ubuntu-latest
    steps:
      - uses: StarChecker/star-guard/action@main
        with:
          api-url: ${{ vars.STAR_GUARD_API_URL }}   # публичный адрес сервера Star Guard
          fail-on: critical                          # none | medium | high | critical
```

| Вход | По умолчанию | Описание |
|---|---|---|
| `api-url` | — | адрес сервера Star Guard, доступный из раннера |
| `github-token` | `github.token` | чтение файлов PR и комментарии |
| `hours` | `6` | окно анализа |
| `fail-on` | `none` | уровень, начиная с которого job падает |
| `comment` | `true` | писать ли комментарий в PR |

Выходы: `flagged` (сколько новых зависимостей с уровнем ≥ medium), `checked`.

## Ограничения

- Сервер должен быть доступен из интернета (раннер GitHub не видит localhost).
- Сводки берутся только из индекса: зависимость вне топ-N за окно получает
  «не отслеживается».
- Поддержаны npm и pip; Maven/Gradle/Go — следующий шаг (маппинг пакета на
  репозиторий там сложнее).

Тесты: `node --test action/test/*.test.js` (без внешних пакетов).
