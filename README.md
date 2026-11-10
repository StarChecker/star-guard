# Star Guard

Репозиторий учебного хакатона ИТМО ИПКН.

[![CI](https://github.com/StarChecker/star-guard/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/StarChecker/star-guard/actions/workflows/ci.yml)

**Star Guard Core PoC** — сервис, который по названию репозитория скачивает события
из [GH Archive](https://www.gharchive.org/), считает статистику и прогоняет её через
движок suspicious score. Проверки C1–C5 в PoC **замоканы** (`score.mocked = true`),
движок, веса и explain — настоящие.

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
- реальная проверка C3 «Дисбаланс звёзды/активность», включается флагом
  (см. [Конфигурация](#конфигурация));
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
```

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/api/v1/repos/{owner}/{name}` | статистика + suspicious score |
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

Ошибки — в формате RFC 9457 `ProblemDetail` с полем `code`
(`INVALID_REQUEST`, `REPO_NOT_FOUND_IN_WINDOW`, `ARCHIVE_UNAVAILABLE`,
`INTERNAL_ERROR`).

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
- `app.scoring.mock-mode`, веса проверок, усиления и пороги уровней;
- `app.scoring.mode` — `mock` (по умолчанию) или `real`: общий переключатель
  реализаций проверок;
- `app.scoring.checks.<id>.mode` — точечное переопределение для одной проверки,
  по умолчанию `${app.scoring.mode:mock}`; рядом лежат её пороги
  (`app.scoring.checks.burst.*` для C1 и `app.scoring.checks.engagement.*` для C3).
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

## Тесты

```bash
mvn test
```

45 тестов: парсер NDJSON, агрегатор, движок score, мок-профили, Feign-клиент
через WireMock, контроллер, интеграционный сценарий на фикстуре, проверка
`/v3/api-docs` и реальный C3 (формула, пороги, переключатель mock/real,
фикстуры `star-stuffing` / `healthy-lib`). Все тесты работают офлайн.

## CI/CD

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — сборка и тесты на JDK 25
  для push в `main`/`feature/**` и pull request;
- [`.github/workflows/release.yml`](.github/workflows/release.yml) — по тегу `v*`
  собирает jar и публикует GitHub Release.

## Ограничения PoC

- проверки C2, C4, C5 замоканы; C1 и C3 имеют реальные реализации, но по
  умолчанию работают в режиме `mock`, чтобы демо-профили давали стабильный score
  ([docs/core-plan.md](docs/core-plan.md), `CHECKS.md`);
- GH Archive не содержит даты создания аккаунта и глобальных индексов акторов,
  поэтому `DataAvailability` в PoC фиксирует отсутствие этих блоков;
- GH Archive отдаёт только часовые дампы большого размера: 24 часа — это
  несколько ГБ трафика, поэтому по умолчанию окно 6 часов;
- фикстуры `demo/star-guard`, `demo/star-stuffing`, `demo/healthy-lib`
  синтетические и сдвигаются во времени в текущее окно по самому позднему
  событию среди всех файлов — новые фикстуры не должны выходить за
  `2026-11-09T02:59:30Z`, иначе сдвинутся окна существующих;
- Kafka, Redis, SSE, trending и UI в PoC отсутствуют по плану.
