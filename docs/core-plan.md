# Star Guard — план core PoC (v1)

> Статус: черновик на согласование · Ветка: `feature/core`
>
> Основа: командные документы `README.md`, `PLAN.md`, `TASKS.md`, `CHECKS.md`.
> Этот документ заменяет предыдущую редакцию плана: **сначала делаем PoC-срез,
> инфраструктурные модули (Kafka, Redis, SSE, UI) откладываем.**

---

## 1. Цель PoC

Доказать сквозной сценарий end-to-end:

```
GET /api/v1/repos/owner/name
        ↓
клиент качает часовые дампы GH Archive и фильтрует события репозитория
        ↓
сервис считает статистику (звёзды, форки, пушы, уникальные акторы, динамика)
        ↓
движок scoring прогоняет проверки C1–C5 (замоканы) и собирает suspicious score
        ↓
REST отдаёт статистику + score с explain; Swagger позволяет отправить запрос
```

**Критерий успеха демо:** открыть Swagger UI, отправить запрос по реальному
репозиторию, получить статистику за окно последних часов и score, посчитанный
настоящим движком на мок-проверках. Без Docker, без Kafka, без Redis.

**Чего в PoC нет:** Kafka/Redpanda, Redis/ClickHouse, SSE/WebSocket, trending,
30-дневные окна, история аккаунтов, собственный UI, Grafana, Docker Compose.
Эти части остаются в плане команды на следующие итерации — PoC не должен им
мешать (см. §14 «Путь к MVP»).

---

## 2. Scope первой версии

| ID | Фича | Приоритет | Критерий готовности |
|----|------|-----------|---------------------|
| F1 | Доменная модель (`RepoRef`, `GhEvent`, `RepoStats`, `RepoSnapshot`, `RepoScore`) | Must | Модели неизменяемы, лежат отдельно от DTO API |
| F2 | Клиент GH Archive: скачивание часовых дампов, streaming-парсинг, фильтр по репозиторию | Must | На реальном репозитории возвращает события за окно; malformed-строки не роняют запрос |
| F3 | Агрегация статистики по репозиторию | Must | Счётчики и динамика сходятся с ручной проверкой на фикстуре |
| F4 | Сервис отчёта: статистика + snapshot для scoring | Must | Оркестрирует клиент, агрегатор и движок; контроллер остаётся тонким |
| F5 | Мок-проверки C1–C5 + настоящий `ScoringEngine` | Must | Score/level/explain считаются движком по весам из `CHECKS.md`; в ответе видно `mocked: true` |
| F6 | REST-контроллер по репозиторию | Must | `GET /api/v1/repos/{owner}/{name}` с валидацией, кодами ошибок и snake_case JSON |
| F7 | OpenAPI + Swagger UI | Must | `/swagger-ui.html` открывается, запрос отправляется, схемы и примеры заполнены |
| F8 | Офлайн-режим на фикстурах | Must | `app.archive.mode=fixture` поднимает сервис без сети; используется в тестах и как fallback на демо |
| F9 | Кэш скачанных часов | Should | Повторный запрос по тому же репозиторию не перекачивает файлы (TTL) |
| F10 | Тесты: парсер, клиент (WireMock), агрегатор, движок, контроллер, `/v3/api-docs` | Must | `mvn test` зелёный без доступа к сети |
| F11 | README модуля: запуск, запросы, ограничения PoC | Must | По README сервис поднимается с чистой машины |

**Stretch (только если PoC готов раньше):** скачивание часов параллельно
(если не попадёт в F2 сразу), экспорт `docs/openapi.yaml` в файл, простой
HTML за пределами Swagger.

---

## 3. Ключевое уточнение: как устроен GH Archive

GH Archive **не имеет query API** — это публичные почасовые дампы всех событий
GitHub в формате NDJSON, сжатые gzip:

```
https://data.gharchive.org/2026-11-10-6.json.gz
```

Поэтому «клиент, который отправляет запрос в GH Archive» — это HTTP-клиент,
который:

1. по окну времени вычисляет список часовых файлов (UTC);
2. скачивает каждый файл;
3. **потоково** распаковывает (`GZIPInputStream`) и парсит построчно (Jackson);
4. оставляет только события нужного репозитория (`repo.name`, без учёта регистра);
5. отдаёт список `GhEvent` в сервис.

Практические ограничения, которые закладываем в дизайн:

- один часовой дамп — десятки–сотни тысяч событий и десятки–сотни МБ gzip;
- дамп за час появляется с задержкой → окно заканчиваем на `now - lag-hours`
  (по умолчанию 2 часа), а не «сейчас»;
- файл за запрошенный час может отсутствовать (404) — пропускаем и фиксируем
  в метаданных, а не падаем;
- 24 часа — это гигабайты скачивания, поэтому дефолт — 6 часов,
  максимум — 24 (настраивается);
- результат фильтрации кэшируем per `(repo, hour)` — повторные запросы дешёвые.

---

## 4. Архитектура PoC

Один Spring Boot модуль, слои по пакетам. Позже модуль безболезненно
выделяется в multi-module, а источник данных заменяется на Redis.

```text
                 ┌────────────────────────────────────────────┐
 Swagger/HTTP    │  api: RepoController, ErrorHandler, DTO     │
 ───────────────►│                                            │
                 └───────────────────┬────────────────────────┘
                                     ▼
                 ┌────────────────────────────────────────────┐
                 │  service: RepoReportService                │
                 │  stats:   StatsAggregator                  │
                 │  scoring: ScoringEngine + MockedChecks     │
                 └───────┬───────────────────────┬────────────┘
                         ▼                       ▼
                 ┌───────────────┐       ┌──────────────────┐
                 │ archive:      │       │ domain: модели   │
                 │ GhArchiveClient│      │ snapshot/score    │
                 └───────┬───────┘       └──────────────────┘
                         ▼
              data.gharchive.org (или classpath-фикстуры)
```

### Ключевые решения

| # | Решение | Обоснование |
|---|---------|-------------|
| AD-1 | PoC — один модуль Spring Boot, без Kafka/Redis | Минимум инфраструктуры, быстрый вертикальный срез; позже раскладывается по модулям |
| AD-2 | Проверки замоканы **за тем же интерфейсом** `RepoCheck`, движок — настоящий | Замена мока на реальный C1–C5 не меняет ни движок, ни API |
| AD-3 | `RepoStats` (что показываем) и `RepoSnapshot` (что ест scoring) — разные модели | API-контракт не привязан к внутренним полям проверок; snapshot расширяется без ломки API |
| AD-4 | Клиент — streaming-парсинг gzip, фильтрация до агрегации | Не держим в памяти целый часовой дамп (сотни тысяч событий) |
| AD-5 | Окно — по event time (`created_at`), не по времени скачивания | Реплей/фикстуры и реальные дампы считаются одинаково |
| AD-6 | `fixture`-режим с тем же интерфейсом клиента | Тесты без сети, надёжный fallback на демо |
| AD-7 | OpenAPI генерируется springdoc из кода; JSON — snake_case | Документация всегда соответствует реализации, Swagger «Try it out» работает из коробки |

---

## 5. Доменная модель

```java
public record RepoRef(String owner, String name) {
    public String fullName() { return owner + "/" + name; }
}

public enum EventType { WATCH, FORK, PUSH, ISSUES, PULL_REQUEST, OTHER }

/** Событие GH Archive, уже отфильтрованное по репозиторию. */
public record GhEvent(
        String eventId,          // payload.id, для дедупликации
        EventType type,
        long repoId,
        String repoFullName,
        long actorId,
        String actorLogin,
        Instant createdAt        // event time из payload.created_at
) {}

/** Окно анализа: последние N полных часов UTC, заканчивая на now - lag. */
public record TimeWindow(Instant from, Instant to, int hours) {}

public record EventCounts(
        int total,
        int stars,
        int forks,
        int pushes,
        int issues,
        int pullRequests,
        int other
) {}

/** Публичная статистика по репозиторию — то, что отдаём в API. */
public record RepoStats(
        RepoRef repo,
        TimeWindow window,
        EventCounts counts,
        int uniqueActors,
        int starActors,
        int peakStars10m,               // максимум звёзд в любом 10-минутном окне
        List<HourBucket> starsByHour    // для графика, ровно window.hours() точек
) {}

public record HourBucket(Instant hourStart, int stars) {}

/**
 * Вход движка scoring. В PoC заполняется тем, что реально можно посчитать
 * из событий; недостающие блоки помечены в DataAvailability.
 */
public record RepoSnapshot(
        RepoRef repo,
        TimeWindow window,
        Instant capturedAt,
        EventCounts counts,
        int uniqueActors,
        int starActors,
        int[] starsByMinute,            // hours * 60, для будущих C1/C5
        DataAvailability data
) {}

public record DataAvailability(
        boolean actorHistory,           // нет: GH Archive не содержит даты создания аккаунта
        boolean crossRepoOverlap,       // нет: нужен глобальный индекс акторов
        boolean globalHourProfile       // нет: нужен глобальный профиль за 7 дней
) {}
```

**Почему `RepoStats` и `RepoSnapshot` разделены:** статистика — контракт API
(стабильный), snapshot — вход проверок (будет расширяться полями
`actors24h`, `overlap`, `hourHistogram7d` из `CHECKS.md`). В PoC движок получает
урезанный snapshot, но контракт `RepoCheck.evaluate(RepoSnapshot)` сохраняется.

### Scoring-модели (реальный движок, мок-проверки)

```java
public interface RepoCheck {
    String id();                            // "burst", "fresh_accounts", ...
    CheckResult evaluate(RepoSnapshot snapshot);
}

public record CheckResult(
        String checkId,
        double score,                       // 0..1
        double confidence,                  // 0..1
        Map<String, Object> details
) {}

public record RepoScore(
        int score,                          // 0..100
        ScoreLevel level,                   // LOW/MEDIUM/HIGH/CRITICAL
        double confidence,
        boolean insufficientData,
        List<CheckContribution> checks,
        boolean mocked,
        Instant computedAt
) {}

public record CheckContribution(
        String checkId,
        double score,
        double confidence,
        double weight,
        double contribution,
        Map<String, Object> details
) {}

public enum ScoreLevel { LOW, MEDIUM, HIGH, CRITICAL }
```

Алгоритм сборки — как в `CHECKS.md` §4 (веса 0.30/0.22/0.18/0.22/0.08,
порог confidence 0.5, ренормировка по активным, два правила усиления, уровни
0–29 / 30–59 / 60–84 / 85–100). Пороги и веса — в `application.yaml`.

---

## 6. Клиент GH Archive

```java
public interface GhArchiveClient {
    /** События репозитория за окно, в порядке возрастания created_at. */
    List<GhEvent> fetchEvents(RepoRef repo, TimeWindow window);
}
```

### Реализация `HttpGhArchiveClient`

- URL: `{base-url}/{yyyy-MM-dd}-{H}.json.gz`, UTC (base-url по умолчанию
  `https://data.gharchive.org`).
- Для каждого часа окна: GET → `GZIPInputStream` → `BufferedReader` →
  Jackson-парсинг строки; отображение `type`:
  `WatchEvent → WATCH` (звёзды), `ForkEvent`, `PushEvent`, `IssuesEvent`,
  `PullRequestEvent`, остальные — `OTHER` (для счётчика, в статистику не идут).
- Фильтр сразу при парсинге: `repo.name` equalsIgnoreCase `owner/name`;
  события без `repo`/`actor`/`created_at` пропускаются.
- Malformed-строка → счётчик `parseErrors++`, событие пропускается
  (не роняем весь запрос из-за одной битой строки).
- HTTP-ошибки: 404 файла (ещё не опубликован) → час помечается `missing`;
  прочие ошибки → retry (1 повтор с backoff), затем либо исключение
  (`fail-fast=true`), либо пропуск часа с отметкой (`fail-fast=false`, default).
- Параллельность: скачивание часов — в bounded executor
  (`archive.parallelism`, default 4), каждый файл парсится потоково.
- Кэш: `Caffeine` per `(repoFullName, hourStart)` — список отфильтрованных
  событий, TTL 1 час, максимум 256 записей. Повторный запрос по тому же
  репозиторию из кэша.
- Таймауты: connect 5 s, read 30 s (настраиваются).

### Реализация `FixtureGhArchiveClient`

- Тот же интерфейс; читает `classpath:fixtures/gharchive/*.json.gz` (или `.jsonl`).
- Используется в `@SpringBootTest`/`MockMvc`-тестах и на демо:
  `app.archive.mode=fixture` — сервис работает без сети.
- В репозиторий кладём компактную фикстуру реального репозитория
  (200–500 событий за 2–3 часа), чтобы демонстрировать полный ответ.

### Метаданные запроса

Клиент возвращает не только события, но и служебную сводку — она нужна в ответе
API и для отладки:

```java
public record ArchiveFetchResult(
        List<GhEvent> events,
        int hoursRequested,
        int hoursLoaded,
        List<Instant> missingHours,
        int parseErrors,
        boolean cached
) {}
```

---

## 7. Агрегация статистики

`StatsAggregator` — чистая функция `List<GhEvent> + TimeWindow → RepoStats + RepoSnapshot`:

- отбрасывает события вне окна и дубли по `eventId` (идемпотентность);
- считает `EventCounts`: total, stars (WatchEvent), forks, pushes, issues,
  pullRequests, other;
- `uniqueActors` — уникальные `actorId` среди всех событий;
  `starActors` — среди звёзд;
- `starsByMinute[hours * 60]` — звёзды по минутам от начала окна;
- `starsByHour[hours]` — те же данные для графика в API;
- `peakStars10m` — максимум суммы в 10-минутном окне (простая демонстрация
  будущей C1, но это **статистика**, а не проверка);
- `RepoSnapshot` заполняет тем же + `DataAvailability(false, false, false)`.

Крайние случаи: пустой список событий → контроллер отдаёт 404
`REPO_NOT_FOUND_IN_WINDOW`; событие без `created_at`/`actor` — пропущено
и посчитано в `parseErrors`; репозиторий переименован — сравниваем `repo.name`,
при несовпадении будет пусто (оговорено в README).

---

## 8. Мок-проверки и движок

Каждая проверка C1–C5 — отдельный Spring-бин, реализующий `RepoCheck`,
но возвращающий заранее заданные значения:

```java
@Component
public final class MockBurstCheck implements RepoCheck {
    // id = "burst"; score/confidence/detail берутся из профиля mock-mode
}
```

Профили моков (`app.scoring.mock-mode` или PoC-only query-параметр
`mock_mode=healthy|borderline|suspicious`):

| Профиль | Назначение | Ожидаемый итог |
|---------|------------|----------------|
| `healthy` | «здоровый» репозиторий | score 5–20, level LOW |
| `borderline` | «пограничный» | score 30–50, level MEDIUM |
| `suspicious` | «накрутка» для демо | score 75–95, level HIGH/CRITICAL, сработает усиление |

В `details` каждой проверки — `{"mocked": true, "profile": "..."}`;
в `RepoScore.mocked = true`. Движок настоящий: веса, confidence-гейт,
ренормировка, усиления, explain (`contribution = 100 * w * score / W`).

---

## 9. REST API и OpenAPI

### Эндпоинт

| Метод | Путь | Параметры | Ответ |
|-------|------|-----------|-------|
| GET | `/api/v1/repos/{owner}/{name}` | `hours` (1–24, default из конфига), `mock_mode` (PoC-only) | `200 RepoReportResponse`, `400`, `404`, `502`, `503` |
| GET | `/v3/api-docs` | — | OpenAPI 3 JSON (springdoc) |
| GET | `/swagger-ui.html` | — | Swagger UI |

Валидация `owner`/`name`: `^[A-Za-z0-9_.-]{1,100}$`; `hours` — диапазон;
ошибки — RFC 9457 `ProblemDetail` с полем `code`.

### Пример ответа

```json
{
  "repo": {"owner": "spring-projects", "name": "spring-boot"},
  "window": {"from": "2026-11-10T02:00:00Z", "to": "2026-11-10T08:00:00Z", "hours": 6},
  "stats": {
    "total_events": 4231,
    "stars": 142,
    "forks": 21,
    "pushes": 3110,
    "issues": 233,
    "pull_requests": 725,
    "other": 0,
    "unique_actors": 611,
    "star_actors": 138,
    "peak_stars_10m": 9,
    "stars_by_hour": [
      {"hour": "2026-11-10T02:00:00Z", "stars": 18},
      {"hour": "2026-11-10T03:00:00Z", "stars": 24}
    ]
  },
  "score": {
    "score": 10,
    "level": "low",
    "confidence": 0.9,
    "insufficient_data": false,
    "mocked": true,
    "checks": [
      {"id": "burst", "score": 0.10, "confidence": 0.9, "weight": 0.30,
       "contribution": 3.0, "details": {"mocked": true}},
      {"id": "fresh_accounts", "score": 0.10, "confidence": 0.9, "weight": 0.22,
       "contribution": 2.2, "details": {"mocked": true}},
      {"id": "engagement", "score": 0.15, "confidence": 0.9, "weight": 0.18,
       "contribution": 2.7, "details": {"mocked": true}},
      {"id": "actor_overlap", "score": 0.05, "confidence": 0.9, "weight": 0.22,
       "contribution": 1.1, "details": {"mocked": true}},
      {"id": "circadian", "score": 0.10, "confidence": 0.9, "weight": 0.08,
       "contribution": 0.8, "details": {"mocked": true}}
    ]
  },
  "archive": {
    "hours_requested": 6,
    "hours_loaded": 6,
    "missing_hours": [],
    "parse_errors": 0,
    "cached": false
  }
}
```

### Swagger

- `springdoc-openapi-starter-webmvc-ui`; аннотации `@Operation`, `@Parameter`,
  `@ApiResponse`, `@Schema(example = ...)` на DTO.
- `OpenApiConfig`: `info.title = Star Guard Core PoC`, версия `0.1.0`,
  тег «repos», сервер `http://localhost:8080`.
- «Try it out» в Swagger ходит в реальный GH Archive (или в fixture-режиме).
- Контроль контракта: тест `GET /v3/api-docs` — 200 и наличие пути; опционально
  экспорт `docs/openapi.yaml` при сборке, чтобы диффить контракт в PR.

---

## 10. Структура проекта и стек

Один Maven-модуль в корне репозитория (artifactId `star-guard-core`);
выделение multi-module — после PoC.

```text
star-guard/
├── pom.xml
├── mvnw / mvnw.cmd
├── src/main/java/ru/itmo/ipkn/starguard/core/
│   ├── StarGuardCoreApplication.java
│   ├── domain/       RepoRef, GhEvent, RepoStats, RepoSnapshot, RepoScore, ...
│   ├── archive/      GhArchiveClient, HttpGhArchiveClient, FixtureGhArchiveClient,
│   │                 GhArchiveParser, ArchiveProperties, ArchiveFetchResult
│   ├── stats/        StatsAggregator
│   ├── service/      RepoReportService
│   ├── scoring/      RepoCheck, CheckResult, ScoringEngine, MockedChecks, CheckIds
│   ├── api/          RepoController, RepoReportResponse, dto/, ErrorHandler
│   └── config/       AppProperties, OpenApiConfig
├── src/main/resources/application.yaml
├── src/test/java/…
├── src/test/resources/fixtures/…
└── docs/
    ├── core-plan.md            # этот документ
    └── openapi.yaml            # (опционально) экспорт контракта
```

**Стек:** Java 21, Spring Boot 3.5.x (web, validation, actuator),
springdoc-openapi 2.8.x, Jackson, Caffeine (кэш), Maven Wrapper.
**Тесты:** JUnit 5, AssertJ, MockMvc, WireMock (HTTP), springdoc `/v3/api-docs`.
Lombok не используем — records и явные конструкторы.

---

## 11. Конфигурация (`application.yaml`)

```yaml
server:
  port: 8080

spring:
  jackson:
    property-naming-strategy: SNAKE_CASE

springdoc:
  swagger-ui:
    path: /swagger-ui.html

app:
  archive:
    base-url: https://data.gharchive.org
    mode: http                # http | fixture
    default-window-hours: 6
    max-window-hours: 24
    lag-hours: 2              # GH Archive публикует дампы с задержкой
    parallelism: 4
    connect-timeout: 5s
    read-timeout: 30s
    retries: 1
    fail-fast: false          # пропускать недоступные часы, отмечая их в ответе
    cache-ttl: 1h
  scoring:
    mock-mode: healthy        # healthy | borderline | suspicious
    min-confidence: 0.5
    weights:
      burst: 0.30
      fresh_accounts: 0.22
      engagement: 0.18
      actor_overlap: 0.22
      circadian: 0.08
```

Секреты и внешние URL — только через конфиг; пользовательский ввод в URL
не подставляется (защита от SSRF: base-url фиксирован).

---

## 12. Тестирование

| Уровень | Что проверяем |
|---------|----------------|
| Парсер | Строка GH Archive → `GhEvent`; неизвестный тип → `OTHER`; битая строка → пропуск + счётчик |
| Клиент | WireMock отдаёт 2 gzip-файла: фильтрация по репозиторию, порядок событий, 404 часа, retry, кэш |
| Фикстуры | `FixtureGhArchiveClient` отдаёт те же события, что HTTP-клиент на тех же файлах |
| Агрегатор | Эталонная фикстура → точные `EventCounts`, `uniqueActors`, `starsByHour`, `peakStars10m`; дубли и события вне окна |
| Движок | Все три профиля моков дают ожидаемые уровни; explain: сумма contribution = score (с точностью округления) |
| Контроллер | `MockMvc`: 200 по фикстуре, 400 на плохой owner/hours, 404 на репозиторий без событий, 502/503 при недоступном архиве |
| OpenAPI | `/v3/api-docs` 200, путь `/api/v1/repos/{owner}/{name}` и схемы присутствуют |

Все тесты работают офлайн (`fixture`-режим + WireMock), CI не ходит в GH Archive.

---

## 13. Итерации и Definition of Done

| Итерация | Содержание | Оценка |
|----------|------------|--------|
| **0. Согласование** | Ревью этого плана, фиксация вопросов из §16 | 0.5–1 ч |
| **1. Скелет + домен** | `pom.xml`, Boot-приложение, доменные records, `application.yaml`, `OpenApiConfig` | 2–3 ч |
| **2. Клиент архива** | `HttpGhArchiveClient`, парсер, окно/лаг, кэш, `FixtureGhArchiveClient` + фикстура, unit-тесты | 3–4 ч |
| **3. Статистика + scoring** | `StatsAggregator`, `RepoReportService`, мок-проверки, движок, тесты | 2–3 ч |
| **4. REST + Swagger** | Контроллер, DTO, ошибки, валидация, springdoc, MockMvc-тесты | 2–3 ч |
| **5. Демо** | Прогон на реальном репозитории, fixture-fallback, README, скриншот Swagger | 1–2 ч |

Итого ~1,5–2 рабочих дня. **DoD PoC:**

- [ ] `./mvnw spring-boot:run` поднимает сервис, `/swagger-ui.html` открывается;
- [ ] запрос по реальному репозиторию отдаёт статистику и score с explain;
- [ ] `mock_mode=suspicious` меняет уровень на HIGH/CRITICAL без перезапуска;
- [ ] `mode=fixture` работает без сети;
- [ ] `./mvnw test` зелёный офлайн;
- [ ] README: запуск, примеры запросов, известные ограничения PoC;
- [ ] в ответе честно видны `mocked: true` и `parse_errors`/`missing_hours`.

---

## 14. Путь к MVP (как PoC встраивается в общий план команды)

PoC сознательно повторяет будущие границы слоёв:

1. **Данные.** `GhArchiveClient` в MVP заменяется на `RepoSnapshotProvider`,
   который читает агрегаты из Redis (Kafka Streams). `RepoStats` при этом
   собирается из тех же агрегатов. API и движок не меняются.
2. **Проверки.** Каждый мок-Cx заменяется реальной реализацией `RepoCheck`
   по `TASKS.md`; движок, веса и explain уже готовы.
3. **Snapshot.** В `RepoSnapshot` добавляются `actors24h`, `overlap`,
   `hourHistogram7d`, `globalHourShare7d`; `DataAvailability` станет
   вычисляемым, а не константным.
4. **API.** Контракт `RepoReportResponse` остаётся; добавляются trending и SSE.
5. **Инфраструктура.** Docker Compose, Kafka, Redis, Grafana — отдельными
   шагами по `PLAN.md`.

---

## 15. Риски PoC

| Риск | Решение |
|------|---------|
| Часовой дамп большой, скачивание медленное | Streaming-парсинг, default 6 ч, лимит 24 ч, параллельность 4, кэш по часам |
| Последний час ещё не опубликован | `lag-hours: 2`, 404 → `missing_hours`, не ошибка |
| GH Archive недоступен на демо | `mode=fixture` + закоммиченная фикстура, заранее прогреть кэш реальным запросом |
| Выбранный репозиторий неактивен в окне | 404 с понятным `code`; в README — как выбрать активный репозиторий |
| В GH Archive нет даты создания аккаунта | Честный `DataAvailability.actorHistory=false`; C2 реально посчитать пока нельзя — она мокается, как и оговорено |
| Мок-скор примут за настоящий | Явные `mocked: true`, `details.mocked`, дисклеймер в Swagger-описании и README |
| Битые строки в дампе | Пропуск + `parse_errors`, запрос не падает |

---

## 16. Вопросы к согласованию (до начала разработки)

1. **Форма эндпоинта**: один `GET /api/v1/repos/{owner}/{name}` со статистикой
   и score в одном ответе — ок? Или разделить на `/stats` и `/score`?
2. **Окно по умолчанию**: 6 часов, максимум 24, лаг 2 часа — принимаем?
   (24 часа = несколько ГБ скачивания.)
3. **Переключение моков**: PoC-only query-параметр `mock_mode` для демо
   (рекомендую) или только конфиг + перезапуск?
4. **Какие события** в статистику: Watch, Fork, Push, Issues, PullRequest
   (остальные в `other`) — достаточно?
5. **Фикстуры**: коммитим компактную фикстуру реального репозитория для
   офлайн-демо — согласны?
6. **Координаты проекта**: `ru.itmo.ipkn.starguard.core`, артефакт
   `star-guard-core`, Java 21 + Spring Boot 3.5.x — ок?
7. **Формат JSON**: snake_case (как в примерах `CHECKS.md`) для всех ответов —
   подтверждаем?

---

## 17. Политика изменения API

- `RepoReportResponse` и `RepoScore` — публичный контракт PoC: меняются только
  с обновлением OpenAPI и теста `/v3/api-docs`.
- `RepoCheck` / `CheckResult` / движок — контракт из `CHECKS.md`; моки и будущие
  реальные проверки реализуют его без изменений.
- Новая проверка = новый бин + вес в конфиге + фикстура, без правок движка.
