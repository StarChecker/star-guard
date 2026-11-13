# Star Guard

**Индекс подозрительности звёзд на GitHub.** Звёзды на GitHub можно купить, а по ним
выбирают библиотеки — и люди, и AI-агенты. Star Guard оценивает, насколько рост звёзд
репозитория похож на накрутку, и показывает оценку там, где принимается решение:
прямо на странице GitHub, в pull request и в ответах агента.

Проект команды **StarChecker** на хакатоне ИТМО · DevDays Fall 2026.

[![CI](https://github.com/StarChecker/star-guard/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/StarChecker/star-guard/actions/workflows/ci.yml)
[![Pages](https://github.com/StarChecker/star-guard/actions/workflows/pages.yml/badge.svg?branch=main)](https://github.com/StarChecker/star-guard/actions/workflows/pages.yml)

| | |
|---|---|
| 🎤 **Презентация** | [starchecker.github.io/star-guard/deck](https://starchecker.github.io/star-guard/deck/) · исходник [`site/deck/index.html`](site/deck/index.html) |
| 🌐 **Лендинг и установка** | [starchecker.github.io/star-guard](https://starchecker.github.io/star-guard/) |
| 🧩 **Расширение Chrome** | [скачать zip](https://starchecker.github.io/star-guard/star-guard-extension.zip) · [описание](extension/README.md) |
| 🗺 **Архитектура** | [схемы](https://starchecker.github.io/star-guard/architecture/01-overview.html) · [`docs/architecture`](docs/architecture) |
| 📚 **Технический справочник** | [`docs/reference.md`](docs/reference.md) — API, конфигурация, проверки, метрики (прежний README) |

## Что делает

Оценка — число 0–100 с уровнем `low / medium / high / critical`, вердиктом по-русски,
уверенностью, графиком звёзд и объяснением по каждой проверке. Это оценка признаков,
а не доказательство накрутки: формулировки нейтральные, недостаток данных снижает
уверенность, а не «оправдывает» репозиторий.

Где видна оценка:

- **Расширение Chrome** — значок у кнопки Star, свёрнутая карточка в сайдбаре
  (оценка, график, причины по кнопке «Показать разбор»), выбор периода от часов
  до всей истории, цветные точки в Trending, поиске и списках звёзд;
- **GitHub Action** ([`action/`](action/README.md)) — проверяет новые зависимости
  в PR (npm, pip) и пишет комментарий с оценкой;
- **MCP-сервер** ([StarChecker/star-guard-mcp](https://github.com/StarChecker/star-guard-mcp)) —
  AI-агент проверяет зависимость перед тем, как её предложить;
- **Бейдж для README** — `GET /api/v1/badge/{owner}/{name}` в формате shields.io.

## Как оценивается

Данные — история звёзд и активность из GitHub API, собственный трекер (снимки
счётчиков раз в 5 минут, PostgreSQL), события GH Archive и пул подозрительных
аккаунтов. Проверки:

| Признак | Что ищем |
|---|---|
| Резкий рост | дневной приток против медианы прошлых 28 дней; короткие всплески по снимкам трекера |
| Звёзды без разработки | приток звёзд без коммитов, issues и PR |
| Много форков на звезду | у молодых проектов — массовые форки вместе со звёздами |
| Необычный суточный ритм | распределение по часам против общего профиля GitHub |
| Последующие падения | массовое исчезновение звёзд (чистки аккаунтов) |
| Аккаунты из пула | звёзды от профилей с тысячами звёзд, без активности и с плотными сериями |

Для окон в часах работает индекс топ-1000 по GH Archive с проверками C1–C5
(всплеск, свежие аккаунты, дисбаланс звёзды/активность, пересечение аудиторий,
суточный ритм). Обоснование признаков и их слабые места — в
[`docs/heuristics.md`](docs/heuristics.md), формулы — в
[`docs/reference.md`](docs/reference.md).

## Архитектура

```text
 Chrome-расширение ─┐                       ┌─ GitHub API (история звёзд, активность)
 GitHub Action ─────┼─► Star Guard Core ────┼─ GH Archive (часовые дампы → индекс топ-1000)
 MCP-сервер ────────┘   Spring Boot · :8080 └─ PostgreSQL (трекер звёзд, пул аккаунтов)
                        ScoringEngine → score · level · explain · таймлайн
```

| Каталог | Что внутри |
|---|---|
| [`src/`](src) | Core: Java 25, Spring Boot 4 — REST API, клиенты GitHub и GH Archive, индекс, трекер, проверки, движок оценки |
| [`extension/`](extension) | расширение Chrome (Manifest V3, без сборки) |
| [`action/`](action) | GitHub Action для pull request (Node 20) |
| [`site/`](site) | лендинг, презентация, собранное расширение — публикуются в GitHub Pages |
| [`scripts/`](scripts) | сборка zip расширения и лендинга |
| [`docs/`](docs/README.md) | справочник, эвристики, архитектура, планы и отчёты |
| [`server.json`](server.json) | адрес сервера для расширения и лендинга — меняется без деплоя |

## Сборка и запуск

### Требования

- JDK 25 (Maven ставить не нужно — есть `./mvnw`) **или** Docker с Compose;
- Node 20 — для тестов Action и сборки расширения со своим адресом сервера;
- токен GitHub (read-only, без scopes) — для анализа по истории GitHub; без него
  работают индекс GH Archive и офлайн-демо.

### Docker Compose (рекомендуется)

```bash
cp .env.example .env          # впишите GITHUB_TOKEN; режимы — в комментариях файла
docker compose up --build -d  # Core на :8080 + PostgreSQL
curl http://localhost:8080/actuator/health
```

Офлайн-демо без интернета и токена — на синтетических фикстурах:

```bash
APP_ARCHIVE_MODE=fixture APP_SCORING_MODE=mock docker compose up --build -d
curl "http://localhost:8080/api/v1/repos/demo/star-guard?hours=3&mock_mode=suspicious"
```

Порт меняется переменной `STAR_GUARD_PORT`, остановка — `docker compose down`.

### Без Docker

```bash
./mvnw -B verify                                       # сборка и все тесты (офлайн)
java -jar target/star-guard-core-0.1.0-SNAPSHOT.jar    # реальные данные
java -jar target/star-guard-core-0.1.0-SNAPSHOT.jar --app.archive.mode=fixture   # офлайн-демо
```

Swagger UI — `http://localhost:8080/swagger-ui.html`, OpenAPI —
[`docs/openapi.yaml`](docs/openapi.yaml). Без Compose по умолчанию работают только
индекс GH Archive и фикстуры: история GitHub включается `GITHUB_TOKEN=… APP_GITHUB_ENABLED=true`,
трекер звёзд — `APP_TRACKER_ENABLED=true` (нужен PostgreSQL, см. `compose.yaml`).

### Расширение

Готовый архив — [на лендинге](https://starchecker.github.io/star-guard/star-guard-extension.zip)
или в [`site/star-guard-extension.zip`](site/star-guard-extension.zip). Установка:
распаковать → `chrome://extensions` → «Режим разработчика» → «Загрузить распакованное».
Для разработки можно загрузить папку [`extension/`](extension) напрямую.

Адрес сервера расширение берёт по приоритету: из своих настроек → из
[`server.json`](server.json) (перечитывается раз в 5 минут, менять можно прямо на GitHub)
→ запасной адрес сборки. Собрать архив:

```bash
scripts/pack-extension.sh star-guard-extension.zip
STAR_GUARD_API_URL=https://example.org scripts/pack-extension.sh out.zip   # свой запасной адрес
```

Все состояния карточки без сервера — [`extension/preview/index.html`](extension/preview/index.html).

### Лендинг и Action

```bash
scripts/build-site.sh _site && python3 -m http.server -d _site 8000   # лендинг локально
node --test action/test/*.test.js                                    # тесты Action
```

## CI/CD

| Workflow | Когда | Что делает |
|---|---|---|
| [`ci.yml`](.github/workflows/ci.yml) | push в `main`, `feature/**`, PR | тесты Core на JDK 25, проверка расширения, тесты Action, zip расширения; деплой на сервер — ручной запуск на `main` |
| [`pages.yml`](.github/workflows/pages.yml) | push в `main` (лендинг, расширение, презентация) | собирает и публикует GitHub Pages |
| [`release.yml`](.github/workflows/release.yml) | тег `v*` | jar в GitHub Release |

## Ограничения

- С 30.06.2026 GitHub не отдаёт список поставивших звезду, а GH Archive видит малую
  часть звёзд, поэтому основная оценка строится по агрегатам: история звёзд,
  активность, снимки трекера. Пул аккаунтов неполный — отсутствие совпадений не
  снижает риск.
- Пороги откалиброваны на синтетике и ручной проверке, а не на размеченной выборке;
  релизы и публикации тоже дают резкий органический рост.
- Подробно — в [`docs/reference.md`](docs/reference.md#ограничения-poc) и
  [`docs/live-verification.md`](docs/live-verification.md).

## Команда StarChecker

| | Область |
|---|---|
| `@h0o0p0e` | MCP-сервер |
| `@Ar6yZxZ` | потоковый парсер GH Archive, почасовые агрегаты, long-tail индекс |
| `@mpstrkv` | tiered-индекс, пересечение аудиторий на 10k |
| `@nenerower` | интервалы всплесков и подозрительные аккаунты в API и UI |
| `@writenowme` | UX: расширение, бейдж, проверка зависимостей в PR, метрики и память |

Распределение задач — [`docs/task-board.md`](docs/task-board.md).
