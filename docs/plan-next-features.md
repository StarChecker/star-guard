# Star Guard — план следующих фич (пул задач)

> Статус: **согласовано**, решения §0 зафиксированы · База: `main` = `2e294e3` (2026-11-10)
> Проверка базы: `mvn clean test` на JDK 25 — **160 тестов, 0 ошибок**.
> Текущая карта фич: [docs/feature-map.md](feature-map.md).
> Статусы задач: [docs/task-board.md](task-board.md) — разработчики отмечают
> ⬜ не взят / 🟡 в работе / ✅ выполнено.

## 0. Решения по согласованию

| Вопрос | Решение |
|--------|---------|
| 1–3 с для произвольного репозитория | Best-effort для **случайного активного** репозитория: long-tail компактный индекс всех репозиториев окна. Если событий нет — честный `202` + фоновый скан |
| Объём индекса | Основной tier — **top-10 000**; long-tail — компактные агрегаты для остальных репозиториев окна. `top-n` конфигурируемый |
| Память | Индекс — **1 ГБ** (`app.index.memory-budget-mb: 1024`), JVM heap — **`-Xmx1280m`** (1 ГБ индекса + ~20% на остальное); eviction/backpressure, без OOM на ноутбуке |
| MCP / «нейросеть» | MCP-сервер **не делает внешних LLM-запросов**. Трактовка детерминированная: наш сервис уже отдаёт `score/level/verdict/checks`, MCP маппит их в категории по порогам. Claude Code — только MCP-клиент |
| Транспорт MCP | Для PoC — **только stdio** (Claude Code). Streamable HTTP — P2/после PoC |
| Логины аккаунтов | Показываем login по умолчанию; режим hash — как fallback/приватный режим |
| Холодные репозитории | Полный score — async scan (`202` + polling). GitHub-API preview — **отдельная фича F5, делаем сразу после F4** |
| Где показывать статистику | **Popup** (и та же карточка в sidebar): аккаунты, интервалы всплеска, форки/issues/пушы/контрибьюторы. В popup — **топ-5 подозрительных аккаунтов + «ещё»** |

---

## 1. Что уже есть по четырём пунктам

### 1.1. Статистика в расширении — частично сделано

Есть:

- `GET /api/v1/repos/{owner}/{name}` отдаёт `stats` и `stars_timeline`:
  `forks`, `issues`, `pull_requests`, `pushes`, `unique_actors`, `peak_stars_10m`,
  звёзды по часам (`api/dto/StatsDto.java`, `StarTimelineDto.java`);
- в карточке уже видны форки/issues/PR/пушы и пик звёзд
  (`extension/src/ui.js`, `evidence()`);
- таймлайн делит столбцы на обычные/подозрительные (`bar-ok` / `bar-bad`),
  есть `suspicious_stars` и `suspicious_by_check`.

Не хватает:

- списка подозрительных **аккаунтов** (login, причина fresh/overlap, дата аккаунта);
- явных **интервалов всплеска** (`spike_intervals[]`) для подсветки участка;
- числа **контрибьюторов** (сейчас `unique_actors` включает звёзды, в индексе
  акторы суммируются по часам без дедупликации);
- вывода всего этого в **popup** и preview-фикстуры.

### 1.2. MCP-сервер — не сделано

В репозитории нет MCP-зависимостей. REST уже даёт нужное: report, scans, batch,
index, badge. Внешняя LLM не нужна: интерпретация строится на `level`, `verdict`,
`checks[].summary` и порогах.

### 1.3. Ускорение парсинга и масштаб — не сделано

Сейчас top-1000. Узкие места:

- **два полных прохода** по часам окна (`RepoIndexBuilder`);
- **Jackson `readTree`** на каждую строку: строится дерево всего события,
  включая неиспользуемый `payload`;
- **память на акторов**: `RepoHourAggregate` хранит `List<ActorActivity>`
  (≈230–260 Б на актор-час);
- `rank()`/`top()` сортируют индекс на каждый запрос, `starsByMinute()` клонирует массив;
- нет дедупликации `event_id`; старый и новый snapshot живут одновременно.

### 1.4. Добавление репозитория и 1–3 с — частично сделано

Есть async-скан (`POST /api/v1/scans` + polling, 2 воркера, кэш 30 минут).
Нет long-tail индекса и эндпоинта «добавить/закрепить репозиторий»; холодный
скан идёт десятки секунд–минуты.

---

## 2. Целевые показатели (SLO)

| Метрика | Сейчас | Цель |
|---------|--------|------|
| Top-tier индекса | 1 000 | **10 000** (detail-агрегаты); long-tail — компактно для остальных репозиториев окна |
| Память индекса | до ~1–2 ГБ | **≤1 ГБ** (`app.index.memory-budget-mb: 1024`), JVM `-Xmx1280m`, eviction/backpressure |
| Ответ по top-tier | O(1), но сортировки/клоны | **p95 < 100 мс** |
| Ответ по long-tail | — | **1–3 с** для репозитория с событиями в окне |
| Холодный/пустой репозиторий | скан минуты | **202** + прогресс; preview — отдельная фича F5 |
| Парсинг дампа | Jackson tree | **2–5× быстрее**, только нужные поля |
| Сборка индекса | 2 прохода, полный rebuild | первый build — 2 прохода; далее **инкрементально по часу** |

---

## 3. Архитектурные решения

| # | Решение | Детали |
|---|---------|--------|
| A1 | **Tier A — top-10k** | счётчики, `starsByMinute[60]`, star actor IDs; полные `ActorActivity` (login/accountCreatedAt) — только **detail-tier top-1k** |
| A2 | **Tier B — long-tail** | для остальных репозиториев окна: счётчики + разреженные минуты звёзд + star actor IDs; без логинов/истории аккаунтов |
| A3 | **Память** | индекс — budget 1 ГБ, JVM heap — `-Xmx1280m` (индекс + ~20% на остальное); eviction самых старых часов/редких репозиториев, backpressure; long-tail без `ActorActivity`; top-tier — примитивы, не boxed-записи |
| A4 | **Парсер** | streaming Jackson `JsonParser`, только `id/type/created_at/repo/actor`; lightweight событие для pass 1; ранний фильтр по `repo.name` |
| A5 | **Обновление** | первый build — два прохода; далее почасовые агрегаты + парсинг только нового часа, сдвиг окна |
| A6 | **1–3 с** | long-tail lookup для репозитория с событиями; если нет — `202` + async scan; без внешних API в F4 |
| A7 | **MCP** | отдельный `mcp/` на Node/TypeScript (`@modelcontextprotocol/sdk`), **stdio в PoC**; Streamable HTTP — P2; **только вызовы нашего REST**, без внешних LLM |
| A8 | **Интерпретация** | детерминированная: `level` → категория (`healthy / borderline / suspicious / critical / insufficient`), причины из `verdict` и `checks[].summary`, рекомендация по порогам |

---

## 4. Пул задач

### F1. Расширение: аккаунты, интервалы всплеска, контрибьюторы

**Цель:** в **popup** (и sidebar-карточке) видно: подозрительность аккаунтов,
участок предполагаемого всплеска (цветом), форки/issues/пушы/контрибьюторы.

| ID | Задача | Оценка | Зависимости |
|----|--------|--------|-------------|
| F1-1 | API `spike_intervals[]` в `StarTimelineDto` (from/to, stars, ratio, peak, suspicious) | 1–2 | — |
| F1-2 | API `suspicious_actors[]` (login, reasons fresh/overlap, account_created_at, first_seen, shared_repos) и `contributors` в `StatsDto`; лимит для popup — 5 | 3 | C2/C4 real; F3-3 |
| F1-3 | Extension UI: цветной интервал на графике, список аккаунтов (**в popup — топ-5 + «ещё»**), плитки форков/issues/пушей/контрибьюторов — popup и sidebar | 3 | F1-1, F1-2 |
| F1-4 | Preview-фикстуры, MockMvc/JUnit, OpenAPI, README | 2 | F1-1…F1-3 |

**Реализация:**

- `StarTimelineService` уже знает подозрительные бакеты; добавить интервалы из
  `starsByMinute` + C1 `details` (`window_start`, `max_burst_10m`, `ratio`).
- Аккаунты: `ActorActivity.login` есть в прямом скане и `RepoHourAggregate.actors`;
  для top-tier merge по часам с дедупликацией по `actorId`; для long-tail login
  только если сохранён detail-tier, иначе hash/причины.
- Контрибьюторы: distinct non-star actors (fork/push/issues/PR), дедуп между часами.
- Логины по умолчанию; `app.api.actor-logins: show|hash`, лимит
  `app.api.suspicious-actors-limit`.
- Popup (`extension/src/popup.js`) и sidebar используют общий `ui.js`;
  добавить блоки в оба режима, preview-фикстуры.
- **DoD:** в popup и sidebar на `demo/farm-target` видны цветной интервал,
  аккаунты с причинами, форки/issues/пушы/контрибьюторы; на `demo/organic-app`
  нейтрально; тесты/preview.

### F2. MCP-сервер под Claude Code (детерминированная трактовка)

**Цель:** перед использованием open-source библиотеки агент в Claude Code
вызывает MCP-инструмент и получает score, уровень, категорию и причины.
**Внешние LLM-запросы не выполняются** — трактовка по порогам нашего сервиса.

| ID | Задача | Оценка | Зависимости |
|----|--------|--------|-------------|
| F2-1 | Каркас `mcp/` на TypeScript + MCP SDK; **stdio** для Claude Code | 2 | — |
| F2-2 | Инструменты: `assess_dependency`, `get_repo_report`, `start_scan`, `get_scan`, `list_indexed_repos`, `batch_summaries`, `get_badge` | 2 | F2-1 |
| F2-3 | Детерминированная трактовка: `level` → category, recommendation, reasons из `verdict`/`checks[].summary`; пороги в конфиге MCP | 1 | F2-2 |
| F2-4 | Guardrails: `mocked`/`insufficient_data`/покрытие архива, дисклеймер, без внешних запросов | 1 | F2-3 |
| F2-5 | Конфиг Claude Code (`.mcp.json`/`claude mcp add`), Compose, Dockerfile, тесты с fake core; **Streamable HTTP — P2, не входит в DoD PoC** | 1–2 | F2-4 |

**Схема ответа `assess_dependency`:**

```json
{
  "repository": "owner/name",
  "score": 72,
  "level": "high",
  "verdict": "Заметные признаки накрутки: главный сигнал — «пересечение аккаунтов».",
  "interpretation": {
    "category": "suspicious",
    "confidence": "medium",
    "reasons": ["30 аккаунтов ставят звёзды 5 репозиториям", "пик 31 звезда за 10 минут"],
    "recommendation": "review_before_use",
    "caveats": ["часть проверок в mock-режиме", "2 часа архива отсутствуют"]
  },
  "disclaimer": "Это признаки, а не доказательство накрутки."
}
```

- Категории: `healthy` (low), `borderline` (medium), `suspicious` (high),
  `critical`, `insufficient_data`.
- Рекомендации: `use`, `use_with_caution`, `review_before_use`, `avoid`,
  `insufficient_data`.
- Для репозитория вне индекса: `404 REPO_NOT_INDEXED` → `start_scan`, polling
  с таймаутом; ответ возвращает `scan_id`/прогресс, если не успел.
- Claude Code:
  ```bash
  claude mcp add star-guard -- node mcp/dist/index.js
  # env: STAR_GUARD_API_URL; транспорт stdio
  ```
- **DoD:** MCP подключается в Claude Code по stdio; индексный репозиторий отдаёт
  структуру выше; внешний — запускает скан; тесты/документация. Никаких
  API-ключей LLM и исходящих запросов, кроме нашего сервиса. Streamable HTTP —
  отдельная P2-фича после PoC.

### F3. Парсинг, 10k top-tier и long-tail без OOM

| ID | Задача | Оценка | Зависимости |
|----|--------|--------|-------------|
| F3-1 | Streaming-парсер: `JsonParser`, только нужные поля, lightweight pass 1, early repo filter | 3 | — |
| F3-2 | Почасовые compact-агрегаты: не парсить час дважды, инкрементальный сдвиг окна | 3 | F3-1 |
| F3-3 | Tiered-модель: top-10k compact + detail-1k actor-профили + long-tail counts/star IDs; primitive arrays | 4 | F3-2 |
| F3-4 | Быстрый запрос: precomputed `totalStars`/rank, read-only `starsByMinute`, дедуп `event_id` | 2 | F3-3 |
| F3-5 | C4 на 10k: инвертированный `actor→repos` или предвычисленные overlaps; candidate pool | 2 | F3-3 |
| F3-6 | Memory budget 1 ГБ + JVM `-Xmx1280m`; бенчмарк heap/build/p95; eviction/backpressure | 2 | F3-3 |

**Реализация:**

- `GhArchiveParser` — `parseLight` для pass 1 (type/repo/actor) и `parse` для pass 2.
- `RepoHourAggregate` — `int[60]` без клона на чтение, `long[]` star actor IDs;
  detail-tier — `List<ActorActivity>`.
- `RepoIndexSnapshot` — `topByStars` + `ranks`, не сортировать на каждый запрос.
- `RepoIndexBuilder` — первый build два прохода, дальше новый час + сдвиг окна.
- `RepoIndexLifecycle` — атомарная подмена и освобождение старого snapshot.
- Конфиг: `app.index.top-n: 10000`, `detail-tier-size: 1000`,
  `memory-budget-mb: 1024`, `max-per-repo-actors`, `parse-threads`.
- **DoD:** 10k top-tier + long-tail; индекс ≤1 ГБ при JVM `-Xmx1280m`;
  парсер 2–5× быстрее; p95 <100 мс; 160+ тестов; отчёт с цифрами.

### F4. Произвольный репозиторий: long-tail, добавление в индекс, 1–3 с

| ID | Задача | Оценка | Зависимости |
|----|--------|--------|-------------|
| F4-1 | Long-tail компактный индекс всех репозиториев окна (counts + star IDs + звёздные минуты в пределах 1 ГБ) | 4 | F3-3 |
| F4-2 | `POST /api/v1/index/repos` / `PUT /api/v1/index/repos/{owner}/{name}`: добавить/закрепить репозиторий, идемпотентно; 200 с отчётом или 202 со scan id | 2 | F4-1 |
| F4-3 | Приоритетный scan-job: очередь, прогресс, кэш, TTL pinned-репозиториев, выживание при rebuild | 2 | F4-2 |
| F4-4 | Тесты: long-tail ≤3 с, cold → 202, идемпотентность, pinned после rebuild; бенчмарк | 1 | F4-1…F4-3 |

**Семантика:**

- репозиторий активен в окне и есть в long-tail → **200 за 1–3 с**;
- нет событий/нет long-tail → **202** + фоновый скан (preview — F5);
- «добавить в индекс» = закрепить в top-tier на TTL; если уже в long-tail,
  промоушен мгновенный, иначе 202 + приоритетный скан.

**DoD:** добавление идемпотентно; warm-репозиторий ≤3 с; cold — 202; pinned
переживает пересборку; OpenAPI/README/тесты/бенчмарк.

### F5. GitHub preview для холодных репозиториев (отдельная фича, сразу после F4)

**Цель:** даже если в GH Archive нет событий в окне, показать базовую статистику
из GitHub REST API, пока полный score считается/недоступен.

| ID | Задача | Оценка | Зависимости |
|----|--------|--------|-------------|
| F5-1 | GitHub-клиент: stars/forks/open issues/contributors по REST, кэш, rate limit | 2 | — |
| F5-2 | `GET /api/v1/preview/{owner}/{name}` → базовая статистика + `source=github`, `partial=true` | 1 | F5-1 |
| F5-3 | Extension/MCP: показать preview в состоянии «нет событий в окне» | 1 | F5-2 |
| F5-4 | Тесты (WireMock), OpenAPI, документация | 1 | F5-1…F5-3 |

**DoD:** для репозитория без событий extension показывает базовую статистику
с явной пометкой «нет данных GH Archive»; полный score остаётся async;
конфиг `app.github.enabled` и токен — опционально.

---

## 5. Кросс-задачи

| ID | Задача | Оценка |
|----|--------|--------|
| X-1 | Обновить OpenAPI и README: новые поля, эндпоинты, коды ошибок | 1 |
| X-2 | Метрики: `index.build.duration`, `index.size`, `index.memory`, `parse.lines/sec`, `scan.latency`, `mcp.tool.latency` | 1–2 |
| X-3 | Приватность: логины по умолчанию, режим hash, лимит выборки; MCP без внешних запросов | 1 |
| X-4 | CI: unit/integration офлайн; perf-smoke на синтетическом датасете | 1 |
| X-5 | Документация MCP/Claude Code, extension, on-demand | 1 |

---

## 6. Порядок выполнения и роли

```text
Этап 1 (2–3 дня): F3-1 парсер + F1-1/F1-2 API-поля (параллельно)
Этап 2 (3–4 дня): F3-3 tiered-модель + F1-3 UI (popup/sidebar) + F1-4 тесты
Этап 3 (3–4 дня): F4-1 long-tail + F4-2 add endpoint + F3-4/5/6
Этап 4 (2–3 дня): F2 MCP (stdio) + F5 GitHub preview (параллельно)
Этап 5: F4-4/X-1…X-5, бенчмарки, демо
```

Ориентир: сумма оценок задач **45–55 человеко-дней**; 5 разработчиков — ~2 недели
при параллельной работе. Основной риск — F3-3/F4-1 (память и long-tail).

| Роль | Зона |
|------|------|
| Dev 1 | F2 MCP, API-контракты, X-1 |
| Dev 2 | F3 парсер, инкрементальный индекс, F4 long-tail |
| Dev 3 | F3 tiered-модель, память, C4 на 10k |
| Dev 4 | F1 API + extension UI (popup/sidebar) + preview/tests |
| Dev 5 | F3-6/F4-4 бенчмарки, X-2/X-4, Compose/деплой MCP, F5 |

---

## 7. Риски и открытые вопросы

| Риск | Митигация |
|------|-----------|
| Long-tail не влезает в 1 ГБ | memory budget + eviction/backpressure; long-tail без логинов/`ActorActivity`; top-n=10k; JVM `-Xmx1280m` |
| 1–3 с недостижимы без событий в окне | честный 202 + async; F5 preview делаем сразу после F4 |
| Интерпретация MCP воспринимается как «ИИ-вердикт» | явно писать, что это правило по порогам; дисклеймер «признаки, не доказательство» |
| MCP-сканирование зависит от single-instance `ScanService` | один scan-backend или shared store перед масштабированием |
| Приватность логинов | режим hash, лимит выборки; в popup топ-5 + «ещё» |
| C1 по умолчанию mock → «накрутка» не подсвечивается | для демо включать `app.scoring.checks.burst.mode=real`; иначе нейтральный «всплеск» |

**Зафиксировано (вопросы закрыты):**

1. Индекс — 1 ГБ, JVM — `-Xmx1280m` (индекс + ~20% на остальное).
2. F5 (GitHub preview) делаем сразу после F4, не отдельным спринтом.
3. В popup — топ-5 подозрительных аккаунтов + «ещё».
4. MCP — только stdio; Streamable HTTP — P2 после PoC.
5. Внешние LLM-запросы не делаем; трактовка детерминированная по порогам.

---

## 8. Проверка результата

```bash
mvn -B clean test
mvn -DskipTests package
docker compose up --build -d
curl "http://localhost:8080/api/v1/repos/demo/farm-target?hours=3"
curl "http://localhost:8080/api/v1/index/repos?limit=10000"
claude mcp add star-guard -- node mcp/dist/index.js
```

Для F3/F4 — бенчмарк на синтетическом окне: build time, heap (`-Xmx1280m`,
индекс ≤1 ГБ), p95 ответа, размер top-tier/long-tail. Для F2 — fake core и
проверка в Claude Code через stdio.
