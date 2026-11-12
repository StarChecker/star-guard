# Star Guard

Репозиторий учебного хакатона ИТМО ИПКН.

[![CI](https://github.com/StarChecker/star-guard/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/StarChecker/star-guard/actions/workflows/ci.yml)

**Star Guard Core PoC** — сервис, который по названию репозитория скачивает события
из [GH Archive](https://www.gharchive.org/), считает статистику и прогоняет её через
движок suspicious score. По умолчанию проверки C1–C5 работают в режиме `mock`;
для C1–C4 доступны реальные реализации. Движок, веса и explain — настоящие.

Полный дизайн — в [docs/core-plan.md](docs/core-plan.md), OpenAPI — в
[docs/openapi.yaml](docs/openapi.yaml).

## Что умеет

- клиент GH Archive на Spring Cloud OpenFeign: скачивает часовые gzip-дампы,
  потоково парсит NDJSON и фильтрует события запрошенного репозитория;
- статистика за окно: события по типам, звёзды/форки/пушы/issues/PR,
  уникальные акторы, звёзды по часам и пик за 10 минут;
- движок `ScoringEngine`: веса, порог confidence, ренормировка, правила усиления
  и уровни `low / medium / high / critical`, explain по каждой проверке;
- мок-проверки C1–C5 с профилями `healthy | borderline | suspicious`;
- реальные проверки C1 «Всплеск звёзд», C2 «Свежие и пустые аккаунты»,
  C3 «Дисбаланс звёзды/активность»
  и C4 «Пересечение акторов», включаются флагом (см. [Конфигурация](#конфигурация));
- глобальный индекс звёзд (`WatchEvent` всех репозиториев окна) собирается в том же
  проходе по дампу, с лимитом памяти на час — нужен C4;
- REST API + OpenAPI/Swagger UI;
- офлайн-режим на фикстурах для тестов и стабильного демо;
- кэш часовых выборок, ретраи, обработка отсутствующих часов и битых строк.

## Стек

| Компонент | Версия |
|-----------|--------|
| Java | 25 (LTS) |
| Spring Boot | 4.0.6 (Spring MVC, Validation, Actuator) |
| Spring Cloud OpenFeign | 2025.1.0 (Feign 13.6, `feign-hc5`) |
| springdoc-openapi / Swagger UI | 3.2.0 |
| Jackson | 3.x (из Boot BOM) |
| Caffeine | 3.2.3 (из Boot BOM) |
| Тесты | JUnit 6, AssertJ, Mockito, MockMvc, WireMock 3.13.2 |

## Требования

- JDK 25
- Maven 3.9+ (или `./mvnw`)
- доступ к Maven Central или корпоративному зеркалу (настройте `~/.m2/settings.xml`)

## Запуск

Обычный режим (реальный GH Archive; публичный интернет должен быть доступен):

```bash
mvn spring-boot:run
```

Офлайн-демо на закоммиченной фикстуре (репозиторий `demo/star-guard`):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.archive.mode=fixture
```

Или соберите jar:

```bash
mvn -DskipTests package
java -jar target/star-guard-core-0.1.0-SNAPSHOT.jar --app.archive.mode=fixture
```

Сервис поднимается на `http://localhost:8080`.

### Docker Compose

Для сборки образа и запуска сервиса нужен Docker с поддержкой Compose:

```bash
docker compose up --build -d
docker compose ps
curl http://localhost:8080/actuator/health
```

По умолчанию Compose запускает офлайн-демо на фикстурах. Для проверки API:

```bash
curl "http://localhost:8080/api/v1/repos/demo/star-guard?hours=3&mock_mode=suspicious"
```

Для анализа реальных репозиториев через GH Archive с реальными проверками запустите:

```bash
APP_ARCHIVE_MODE=http APP_SCORING_MODE=real docker compose up --build -d
```

Внешний порт можно изменить переменной `STAR_GUARD_PORT`, например
`STAR_GUARD_PORT=8081 docker compose up --build -d`. Для остановки используйте
`docker compose down`. Dockerfile собирает проект на Java 25 и запускает jar
от непривилегированного пользователя; состояние контейнера проверяется по
`/actuator/health`.

## API

```bash
# реальные данные (если есть доступ к data.gharchive.org)
curl "http://localhost:8080/api/v1/repos/spring-projects/spring-boot?hours=6"

# демо из фикстуры
curl "http://localhost:8080/api/v1/repos/demo/star-guard?hours=3&mock_mode=suspicious"

# демо реального C3 (сервис запущен с --app.scoring.checks.engagement.mode=real):
# накрутка — 120 звёзд при 2 пушах, C3 ≈ 0.92; здоровая библиотека — C3 = 0
curl "http://localhost:8080/api/v1/repos/demo/star-stuffing?hours=3"
curl "http://localhost:8080/api/v1/repos/demo/healthy-lib?hours=3"

# демо реального C1 (сервис запущен с --app.scoring.checks.burst.mode=real):
# в score.checks[0].details видны начало пика, max_burst_10m и ratio
curl "http://localhost:8080/api/v1/repos/demo/star-stuffing?hours=3"
curl "http://localhost:8080/api/v1/repos/demo/healthy-lib?hours=3"

# демо реального C2 (сервис запущен с --app.scoring.checks.fresh-accounts.mode=real):
# ферма - 22 из 25 звёзд от аккаунтов моложе 30 дней, C2 ~ 0.97, level high;
# органика - 10% свежих, C2 = 0; без даты создания аккаунта C2 неактивна
curl "http://localhost:8080/api/v1/repos/demo/fresh-farm?hours=3"
curl "http://localhost:8080/api/v1/repos/demo/organic?hours=3"
curl "http://localhost:8080/api/v1/repos/demo/no-actor-data?hours=3"

# демо реального C4 (--app.scoring.checks.actor-overlap.mode=real):
# ферма из 30 аккаунтов ставит звёзды 5 репозиториям — C4 = 1.0, в explain видны
# пересекающиеся demo/farm-*; живой репозиторий с единичными пересечениями — C4 = 0
curl "http://localhost:8080/api/v1/repos/demo/farm-target?hours=3"
curl "http://localhost:8080/api/v1/repos/demo/organic-app?hours=3"
```

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/v1/repos/{owner}/{name}` | статистика + suspicious score + таймлайн звёзд |
| `POST` | `/api/v1/repos/batch` | сводки до 50 репозиториев (списки GitHub в расширении); только индекс |
| `GET` | `/api/v1/badge/{owner}/{name}` | JSON для бейджа shields.io |
| `POST` | `/api/v1/scans` | фоновый скан GH Archive для репозитория вне индекса → `id` |
| `GET` | `/api/v1/scans/{id}` | статус скана (`hours_done` / `hours_total`), по готовности — отчёт |
| `GET` | `/api/v1/index/repos` | репозитории индекса топ-N |
| `GET` | `/v3/api-docs` | OpenAPI 3 JSON |
| `GET` | `/v3/api-docs.yaml` | OpenAPI 3 YAML |
| `GET` | `/swagger-ui.html` | Swagger UI |
| `GET` | `/actuator/health` | health check |

Параметры запроса:

- `hours` — размер окна в часах, 1–24 (по умолчанию 6);
- `mock_mode` — PoC-профиль мок-проверок: `healthy` (по умолчанию),
  `borderline`, `suspicious`.

Пример ответа (сокращён):

```json
{
  "repo": {"owner": "demo", "name": "star-guard", "full_name": "demo/star-guard"},
  "window": {"from": "2026-11-09T17:00:00Z", "to": "2026-11-09T20:00:00Z", "hours": 3},
  "stats": {
    "total_events": 173,
    "stars": 45,
    "forks": 12,
    "pushes": 70,
    "issues": 18,
    "pull_requests": 22,
    "unique_actors": 38,
    "peak_stars_10m": 9,
    "stars_by_hour": [{"hour": "2026-11-09T17:00:00Z", "stars": 17}]
  },
  "score": {
    "score": 86,
    "level": "critical",
    "confidence": 0.9,
    "mocked": true,
    "checks": [
      {"id": "burst", "score": 0.95, "weight": 0.3, "contribution": 28.5,
       "details": {"mocked": true, "profile": "suspicious"}}
    ],
    "rules_applied": ["multi_trigger", "strong_check"]
  },
  "archive": {"hours_requested": 3, "hours_loaded": 3, "parse_errors": 0}
}
```

Для интерфейса в отчёте есть:

- `score.verdict` и `score.checks[].title` / `summary` — вердикт и причины по-русски,
  нейтральным тоном («признаки накрутки», а не «накручено»);
- `stars_timeline` — звёзды по 10/20/30 минут (`points[].stars`, `points[].suspicious`).
  Подозрительной звезду помечает реальная проверка с ненулевым score: C4 — аккаунты
  фермы, C2 — свежие и пустые аккаунты. В режиме индекса аккаунты известны по часам,
  поэтому подозрительные внутри часа распределены пропорционально (`estimated: true`).

### Бейдж для README

```markdown
![Star Guard](https://img.shields.io/endpoint?url=https://<host>/api/v1/badge/<owner>/<name>)
```

Сообщение — `no signs of fake stars · 7/100`, `suspicious stars · 72/100` и т. д.;
для репозитория вне индекса — серое `not tracked`.

### Расширение и Action

- [extension/](extension/README.md) — расширение Chrome: значок у Star, карточка
  в сайдбаре, окно анализа 1–24 ч (в карточке и настройках), график звёзд,
  точки в списках, скан по кнопке «Проверить»; все состояния —
  в `extension/preview/index.html`;
- [action/](action/README.md) — GitHub Action: проверяет новые зависимости PR
  (npm, pip) и пишет комментарий с оценкой.

Ошибки — в формате RFC 9457 `ProblemDetail` с полем `code`
(`INVALID_REQUEST`, `REPO_NOT_FOUND_IN_WINDOW`, `REPO_NOT_INDEXED`, `INDEX_NOT_READY`,
`SCAN_NOT_FOUND`, `ARCHIVE_UNAVAILABLE`, `INTERNAL_ERROR`).

## Как это работает

```text
GET /api/v1/repos/{owner}/{name}
        │
        ▼
HttpGhArchiveClient (Feign + HC5)      GhArchiveClient
  скачивает {date}-{hour}.json.gz        ▲
  GZIPInputStream → NDJSON → фильтр      │  или FixtureGhArchiveClient
        │                                │  (app.archive.mode=fixture)
        ▼
StatsAggregator → RepoStats + RepoSnapshot
        │
        ▼
ScoringEngine + MockedChecks (C1–C5) → RepoScore (score, level, explain)
        │
        ▼
RepoController → JSON (+ OpenAPI/Swagger)
```

Окно считается по последним полным часам UTC с задержкой публикации GH Archive
(`app.archive.lag-hours: 2`). События фильтруются по `repo.name`, окна и
пересчёты — по event time (`created_at`).

## Конфигурация

Основные параметры — в [application.yaml](src/main/resources/application.yaml).
Параметры C1 находятся в [scoring.yaml](src/main/resources/scoring.yaml),
который подключён через `spring.config.import`:

- `app.archive.mode` — `http` или `fixture`;
- `app.archive.default-window-hours`, `max-window-hours`, `lag-hours`;
- `app.archive.parallelism`, `retries`, `fail-fast`, `cache-ttl`;
- `app.archive.window-end` (env `APP_ARCHIVE_WINDOW_END`) - фиксированный конец окна
  вместо "сейчас минус lag", например `2024-11-10T15:00:00Z`. С конца октября 2026
  в GH Archive около 3 тысяч событий в час и нет пушей
  ([gharchive.org#326](https://github.com/igrigorik/gharchive.org/issues/326)), поэтому для
  демо на реальных данных берите дату до мая 2025. С фиксированным окном индекс
  строится один раз и по cron не пересобирается;
- `app.archive.star-index.*` — сбор индекса звёзд для C4: `enabled`,
  `max-pairs-per-hour` (лимит памяти), `cache-hours`;
- `app.scoring.mock-mode`, веса проверок, усиления и пороги уровней;
- `app.scoring.mode` — `mock` (по умолчанию) или `real`: общий переключатель
  реализаций проверок;
- `app.scoring.checks.<id>.mode` — точечное переопределение для одной проверки,
  по умолчанию `${app.scoring.mode:mock}`; рядом лежат её пороги
  (`app.scoring.checks.burst.*` для C1, `app.scoring.checks.fresh-accounts.*` для C2,
  `app.scoring.checks.engagement.*` для C3, `app.scoring.checks.actor-overlap.*` для C4).
- `app.scoring.checks.burst.enabled` — включает C1; вес, пороги ratio,
  минимальный размер всплеска и длительность прогрева заданы в `scoring.yaml`.

В коде нет магических порогов scoring: всё вынесено в конфиг.

Реальная и мок-реализация проверки разводятся `@ConditionalOnProperty`
по `app.scoring.checks.<id>.mode`, поэтому в контексте всегда ровно один бин
на id. Значение режима валидируется при старте: опечатка (`reall`) роняет
приложение, а не отключает проверку молча.

```bash
# включить только реальный C3
java -jar target/star-guard-core-0.1.0-SNAPSHOT.jar --app.archive.mode=fixture \
  --app.scoring.checks.engagement.mode=real

# включить только реальный C1 для офлайн-демо
java -jar target/star-guard-core-0.1.0-SNAPSHOT.jar --app.archive.mode=fixture \
  --app.scoring.checks.burst.mode=real

# включить все реализованные реальные проверки
java -jar target/star-guard-core-0.1.0-SNAPSHOT.jar --app.scoring.mode=real
```

### C2 "Свежие и пустые аккаунты"

Для каждого актора, поставившего звезду за окно:

```
fresh      = аккаунт моложе new-account-days (30)
             (по actor.created_at, иначе по первому появлению в потоке - только при истории)
             или нет событий, кроме звёзд, за empty-actor-days (90) - только при истории
freshShare = |fresh| / max(n, 1)
s2         = clamp01((freshShare - share-floor) / share-span)     # 0.3 / 0.6
gate       = min(1, n / min-actors)                               # 10
score      = s2 * gate
confidence = confidence * coverage                                # 0.9 * доля акторов с данными
```

"История" - флаг `DataAvailability.actorHistory`: поток событий покрывает
не меньше 90 дней. В PoC он `false`, а в дампах GH Archive нет даты создания
аккаунта, поэтому на реальных данных coverage = 0, confidence = 0 и C2 не входит
в score (`details.reason = no_actor_data`). Акторы без данных считаются не
свежими, так что проверка не завышает score. Дату аккаунта парсер берёт из
`actor.created_at`, если поле есть: так она приходит в демо-фикстурах.

В `details`: `star_actors`, `actors_with_data`, `coverage`, `fresh_actors`,
`new_accounts`, `empty_accounts`, `fresh_share`, `s2`, `gate`, пороги и
`fresh_actor_sample` - до `sample-hashes` (5) обезличенных хэшей свежих акторов
(префикс SHA-256 от `actor.id`).

Дату создания аккаунта можно подтянуть из GitHub REST `GET /users/{login}`:
`app.github.enabled=true` (по умолчанию выключено), токен - `GITHUB_TOKEN`.
Обогащаются только репозитории из топ-`app.github.top-n` (100) индекса, не больше
`max-lookups-per-request` (50) запросов на отчёт, ответы кэшируются на `cache-ttl` (24h),
при rate limit запросы ставятся на паузу. Сама проверка C2 во внешние системы не ходит.

### C3 «Дисбаланс звёзды/активность»

```
engagement = (forks + issues + pulls + pushes) / max(stars, 1)
s3         = clamp01((engagement-threshold − engagement) / engagement-threshold)
gate       = min(1, stars / stars-gate)
score      = s3 × gate
```

Пороги по умолчанию: `engagement-threshold: 0.2`, `stars-gate: 20`.
Если звёзд меньше `min-stars-for-confidence` (5), confidence = 0 и C3 не входит
в score (`details.reason = too_few_stars`). В `details` видны `stars`,
`activity`, `engagement`, `s3`, `gate` и пороги.

### C4 «Пересечение акторов»

По событиям одного репозитория пересечение не посчитать, поэтому клиент архива
в том же проходе по часовому дампу собирает `StarIndex`: для каждого репозитория —
отсортированный массив уникальных акторов, поставивших звезду. Индекс кэшируется
по часу; на час действует лимит `max-pairs-per-hour` (по умолчанию 500 000 пар),
при превышении индекс помечается `truncated`.
В режиме индекса топ-1000 (`app.index.enabled=true`) `StarIndex` строится
в первом проходе `RepoIndexBuilder` и покрывает все репозитории окна, а не только
топ-N; запрос за подокно получает индекс за его часы.

```
A_r        = акторы, поставившие звезду репозиторию за окно
J(r, p)    = |A_r ∩ A_p| / |A_r ∪ A_p|, Jmax — по топ-N кандидатам (candidate-pool: 500)
multiShare = доля A_r, поставивших звёзды ≥ multi-repo-threshold (3) репозиториям за окно
s4         = 0.5·clamp01((Jmax − 0.05) / 0.45) + 0.5·clamp01((multiShare − 0.1) / 0.4)
gate       = min(1, |A_r| / actors-gate)            # actors-gate: 10
score      = s4 × gate
```

Кандидаты — репозитории, у которых есть хотя бы один общий актор, топ-N по числу
общих: у остальных J = 0. Так ловятся и маленькие репозитории фермы вне глобального
топа. Confidence = 0, если индекса нет (`reason = no_cross_repo_index`) или акторов
меньше `min-actors-for-confidence` (5); иначе `0.8 × доля окна в индексе`
(× 0.75, если индекс обрезан). В `details` — `jaccard_max`, `multi_share`,
`top_overlaps` (репозиторий, Jaccard, число общих акторов), размер индекса.

## Тесты

```bash
mvn test
```

Тесты покрывают парсер NDJSON, агрегатор, движок score, мок-профили, Feign-клиент
через WireMock, контроллер, интеграционный сценарий на фикстуре, проверка
`/v3/api-docs`, индекс топ-1000, реальный C2 (формула, история/без истории, пороги,
переключатель mock/real, фикстуры `fresh-farm` / `organic` / `no-actor-data`),
реальный C3 (формула, пороги, фикстуры `star-stuffing` / `healthy-lib`) и реальный C4
(индекс звёзд, формула, покрытие окна, фикстуры `farm-target` / `organic-app`),
API под расширение (тексты explain, таймлайн звёзд, batch, бейдж, скан по запросу).
Все тесты работают офлайн. Тесты Action — `node --test action/test/*.test.js`.

## CI/CD

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — сборка и тесты на JDK 25
  для push в `main`/`feature/**` и pull request; отдельный job проверяет расширение,
  гоняет тесты Action и выкладывает `star-guard-extension.zip`;
- [`.github/workflows/release.yml`](.github/workflows/release.yml) — по тегу `v*`
  собирает jar и публикует GitHub Release.

## Ограничения PoC

- C5 замокана; C1–C4 имеют реальные реализации, но по умолчанию работают в режиме `mock`
  (`app.scoring.mode=mock`), чтобы демо-профили давали стабильный score
  ([docs/core-plan.md](docs/core-plan.md), `CHECKS.md`);
- GH Archive не содержит даты создания аккаунта и истории акторов, поэтому
  `DataAvailability.actorHistory = false`; пересечение акторов (C4) считается
  только в пределах окна запроса, а не за 30 дней, как в исходном плане;
- пороги C4 откалиброваны на синтетике: на реальных дампах доля «мультизвёздных»
  акторов у популярных репозиториев может быть заметной — `multi-floor` и
  `multi-repo-threshold` стоит подобрать по реальному прогону;
- GH Archive отдаёт только часовые дампы большого размера: 24 часа — это
  несколько ГБ трафика, поэтому по умолчанию окно 6 часов;
- фикстуры `demo/star-guard`, `demo/star-stuffing`, `demo/healthy-lib`,
  `demo/fresh-farm`, `demo/organic`, `demo/no-actor-data`, `demo/farm-*`,
  `demo/organic-app` синтетические и сдвигаются во времени в текущее окно по самому
  позднему событию среди всех файлов (вместе с `actor.created_at`). Новые фикстуры
  не должны выходить за `2026-11-09T02:59:30Z`, иначе сдвинутся окна существующих;
- Kafka, Redis, SSE, trending и UI в PoC отсутствуют по плану.
