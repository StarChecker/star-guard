# Star Guard — план следующих фич (пул задач)

> Статус: черновик на согласование · База: `main` = `2e294e3` (2026-11-10)
> Проверка базы: `mvn clean test` на JDK 25 — **160 тестов, 0 ошибок**.
> Текущая карта фич: [docs/feature-map.md](feature-map.md).

## 0. Решения по согласованию

| Вопрос | Решение |
|--------|---------|
| 1–3 с для произвольного репозитория | Делаем best-effort для **случайного активного** репозитория: long-tail компактный индекс всех репозиториев окна. Если событий нет — быстрый GitHub-API preview (<1 с) + async полный скан |
| Объём индекса | Основной tier — **top-10 000**; long-tail — компактные агрегаты для остальных репозиториев окна. `top-n` остаётся конфигом (можно поднять до 100k на сильной машине) |
| Память | Приоритет — **не получить OOM на ноутбуке**: default budget ≤1 ГБ на индекс, конфигурируемый, с eviction/backpressure и без full actor-detail для long-tail |
| MCP | Делаем под **Claude Code**, модель Claude через Anthropic API; публичные данные репозитория разрешено отправлять в LLM |
| Транспорт MCP | Две фичи: **stdio** (Claude Code/Desktop) и **Streamable HTTP** (хостинг/несколько клиентов) |
| Логины аккаунтов | Показываем login по умолчанию; хэши — как fallback/приватный режим |
| Холодные репозитории | Пытаемся собрать статистику: GitHub API preview + фоновый скан; полный suspicious score — по готовности |

---

## 1. Что уже есть по четырём пунктам

### 1.1. Статистика в расширении — частично сделано

Есть:

- `GET /api/v1/repos/{owner}/{name}` отдаёт `stats` и `stars_timeline`:
  `forks`, `issues`, `pull_requests`, `pushes`, `unique_actors`, `peak_stars_10m`,
  звёзды по часам (`api/dto/StatsDto.java`, `StarTimelineDto.java`);
- в расширении уже видны форки/issues/PR/пушы и пик звёзд
  (`extension/src/ui.js`, `evidence()`);
- таймлайн уже делит столбцы на обычные/подозрительные
  (`bar-ok` / `bar-bad`), есть `suspicious_stars` и `suspicious_by_check`.

Не хватает:

- списка подозрительных **аккаунтов** (login, причина: fresh/overlap, дата аккаунта);
- явных **интервалов всплеска** (`spike_intervals[]`) для подсветки участка «накрутки»;
- числа **контрибьюторов** (сейчас `unique_actors` включает звёзды, а индекс
  суммирует акторов по часам без дедупликации);
- вывода этого в верхнюю часть карточки и preview-фикстуры.

### 1.2. MCP-сервер с LLM — не сделано

В репозитории нет MCP/LLM-зависимостей. REST уже даёт всё нужное:
report, scans, batch, index, badge.

### 1.3. Ускорение парсинга и масштаб — не сделано

Сейчас top-1000. Узкие места:

- **два полных прохода** по часам окна (`RepoIndexBuilder`);
- **Jackson `readTree`** на каждую строку (`GhArchiveParser`): строится дерево
  всего события, включая неиспользуемый `payload`; repo name извлекается после
  полного разбора;
- **память на акторов**: `RepoHourAggregate` хранит `List<ActorActivity>`
  (≈230–260 Б на актор-час). Для 100k × 6 часов это единицы–десятки ГБ;
- `rank()`/`top()` сортируют весь индекс на каждый запрос, `starsByMinute()`
  клонирует массив;
- нет дедупликации `event_id` в индексе;
- при подмене snapshot старый и новый живут одновременно (пик памяти ×2).

### 1.4. Добавление репозитория и 1–3 с — частично сделано

Есть async-скан:

- `POST /api/v1/scans` → 202 + `id`, `GET /api/v1/scans/{id}` — прогресс/результат;
- дедупликация, кэш 30 минут, 2 воркера (`scan/ScanService.java`);
- расширение умеет 404 `REPO_NOT_INDEXED` → «Проверить» → polling.

Нет:

- эндпоинта «добавить/закрепить репозиторий в индексе»;
- long-tail индекса, который позволяет отдать произвольный репозиторий за 1–3 с;
- быстрого preview для холодного репозитория.

---

## 2. Целевые показатели (SLO)

| Метрика | Сейчас | Цель |
|---------|--------|------|
| Top-tier индекса | 1 000 | **10 000** (detail-агрегаты), long-tail — компактно для остальных репозиториев окна |
| Память индекса | до ~1–2 ГБ при top-1000 и больших акторах | **default ≤1 ГБ**, конфиг `app.index.memory-budget-mb`; без OOM на ноутбуке |
| Ответ по top-tier | O(1), но сортировка/клоны | **p95 < 100 мс** |
| Ответ по long-tail | — | **1–3 с** для репозитория с событиями в окне |
| Холодный/пустой репозиторий | скан минуты | GitHub preview **<1 с** + async полный score |
| Парсинг дампа | Jackson tree | **2–5× быстрее**, только нужные поля |
| Сборка индекса | 2 прохода, полный rebuild | первый build — 2 прохода; далее **инкрементально по часу** |

---

## 3. Архитектурные решения

| # | Решение | Детали |
|---|---------|--------|
| A1 | **Tier A — top-10k** | счётчики, `starsByMinute[60]`, star actor IDs; полные `ActorActivity` (login/accountCreatedAt) — только **detail-tier top-1k** |
| A2 | **Tier B — long-tail** | для всех остальных репозиториев окна: счётчики + разреженные минуты звёзд + star actor IDs; без логинов/истории аккаунтов |
| A3 | **Память** | budget в МБ, eviction самых старых часов/редких репозиториев, backpressure при сборке; long-tail не хранит `ActorActivity`; top-tier хранит примитивы, не boxed-записи |
| A4 | **Парсер** | streaming Jackson `JsonParser`, только `id/type/created_at/repo/actor`; lightweight событие для pass 1; ранний фильтр по `repo.name` |
| A5 | **Обновление** | первый build — два прохода; далее почасовые агрегаты + парсинг только нового часа, сдвиг окна |
| A6 | **1–3 с** | long-tail lookup для репозитория с событиями; если нет — GitHub preview + async scan; полный score не блокирует ответ |
| A7 | **MCP** | отдельный `mcp/` на Node/TypeScript (`@modelcontextprotocol/sdk`), два транспорта: stdio и Streamable HTTP; LLM — Claude через Anthropic SDK |
| A8 | **LLM-роль** | LLM не пересчитывает score, а интерпретирует готовый отчёт; обязательны `mocked`, `insufficient_data`, покрытие архива, нейтральный тон |

---

## 4. Пул задач

### F1. Расширение: аккаунты, интервалы всплеска, контрибьюторы

| ID | Задача | Оценка | Зависимости |
|----|--------|--------|-------------|
| F1-1 | API `spike_intervals[]` в `StarTimelineDto` (from/to, stars, ratio, peak, suspicious) | 1–2 | — |
| F1-2 | API `suspicious_actors[]` (login, reasons: fresh/overlap, account_created_at, first_seen, shared_repos) и `contributors` в `StatsDto` | 3 | C2/C4 real; F3-3 |
| F1-3 | Extension UI: цветной интервал на графике, список аккаунтов, плитки форков/issues/пушей/контрибьюторов | 3 | F1-1, F1-2 |
| F1-4 | Preview-фикстуры, MockMvc/JUnit, OpenAPI, README | 2 | F1-1…F1-3 |

**Реализация:**

- `StarTimelineService` уже знает подозрительные бакеты; добавить интервалы из
  `starsByMinute` + C1 `details` (`window_start`, `max_burst_10m`, `ratio`).
- Аккаунты: `ActorActivity.login` есть в прямом скане и в `RepoHourAggregate.actors`;
  для top-tier собрать merge по часам с дедупликацией по `actorId`, для long-tail
  показать login только если он сохранён detail-tier, иначе hash/причины.
- Контрибьюторы: distinct non-star actors (fork/push/issues/PR), дедуп между часами.
- Логины показываем по умолчанию; конфиг `app.api.actor-logins: show|hash`
  (по умолчанию `show`), лимит `app.api.suspicious-actors-limit`.
- UI: `extension/src/ui.js` (`timeline()`, `evidence()`, `reportView()`),
  `preview/samples.js`; только `textContent` для данных.
- **DoD:** на `demo/farm-target` видны цветной интервал, аккаунты с причинами,
  форки/issues/пушы/контрибьюторы; на `demo/organic-app` — нейтрально; тесты/preview.

### F2. MCP-сервер под Claude Code + LLM-трактовка

| ID | Задача | Оценка | Зависимости |
|----|--------|--------|-------------|
| F2-1 | Каркас `mcp/` на TypeScript + MCP SDK; stdio и Streamable HTTP | 2 | — |
| F2-2 | Инструменты: `assess_dependency`, `get_repo_report`, `start_scan`, `get_scan`, `list_indexed_repos`, `batch_summaries`, `get_badge` | 2 | F2-1 |
| F2-3 | Claude integration (`@anthropic-ai/sdk`): structured output, модель из env, таймауты/ретраи | 2 | F2-2 |
| F2-4 | Guardrails: `mocked`/`insufficient_data`/покрытие в промпте, fallback на `ExplainTexts`, без секретов в логах | 1–2 | F2-3 |
| F2-5 | Конфиг Claude Code (`.mcp.json`/`claude mcp add`), Compose-сервис, Dockerfile, тесты с fake core/LLM | 2 | F2-4 |

**Сценарий:** «перед использованием open-source библиотеки проверь репозиторий».

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

- Для репозитория вне индекса: `404 REPO_NOT_INDEXED` → `start_scan`, polling
  с таймаутом; LLM получает отчёт только после готовности.
- Claude Code:
  ```bash
  claude mcp add star-guard -- node mcp/dist/index.js
  # env: STAR_GUARD_API_URL, ANTHROPIC_API_KEY, MCP_TRANSPORT=stdio|http
  ```
- Публичные данные (score, stats, explain) разрешено отправлять в Anthropic API;
  API-ключ хранится только в env MCP-сервера.
- **DoD:** инструмент подключается в Claude Code; отдаёт структуру выше для
  индексированного репозитория; для внешнего — запускает скан и возвращает
  прогресс; stdio и HTTP работают; тесты и документация.

### F3. Парсинг, 10k top-tier и long-tail без OOM

| ID | Задача | Оценка | Зависимости |
|----|--------|--------|-------------|
| F3-1 | Streaming-парсер: `JsonParser`, только нужные поля, lightweight pass 1, early repo filter | 3 | — |
| F3-2 | Почасовые compact-агрегаты: не парсить час дважды, инкрементальный сдвиг окна | 3 | F3-1 |
| F3-3 | Tiered-модель: top-10k compact + detail-1k actor-профили + long-tail counts/star IDs; primitive arrays, без `ActorActivity` в long-tail | 4 | F3-2 |
| F3-4 | Быстрый запрос: precomputed `totalStars`/rank, read-only `starsByMinute`, дедуп `event_id`, лимит `/index/repos` | 2 | F3-3 |
| F3-5 | C4 на 10k: инвертированный `actor→repos` или предвычисленные overlaps; candidate pool | 2 | F3-3 |
| F3-6 | Memory budget + бенчмарк: синтетические 10k/long-tail, heap, build time, p95; eviction/backpressure | 2 | F3-3 |

**Реализация:**

- `GhArchiveParser` — `parseLight` для pass 1 (type/repo/actor) и `parse` для pass 2.
- `RepoHourAggregate` — `int[60]` без клона на чтение, `long[]` star actor IDs;
  detail-tier — `List<ActorActivity>`.
- `RepoIndexSnapshot` — `topByStars` + `ranks`, не сортировать на каждый запрос.
- `RepoIndexBuilder` — первый build два прохода, дальше новый час + сдвиг окна.
- `RepoIndexLifecycle` — атомарная подмена и освобождение старого snapshot.
- Конфиг: `app.index.top-n: 10000`, `detail-tier-size: 1000`,
  `memory-budget-mb: 1024`, `max-per-repo-actors`, `parse-threads`.
- **DoD:** 10k top-tier + long-tail; heap в бюджете; парсер 2–5× быстрее;
  p95 <100 мс; 160+ тестов; отчёт с цифрами.

### F4. Произвольный репозиторий: long-tail, добавление в индекс, 1–3 с

| ID | Задача | Оценка | Зависимости |
|----|--------|--------|-------------|
| F4-1 | Long-tail компактный индекс всех репозиториев окна (counts + star IDs + звёздные минуты в пределах бюджета) | 4 | F3-3 |
| F4-2 | `POST /api/v1/index/repos` / `PUT /api/v1/index/repos/{owner}/{name}`: добавить/закрепить репозиторий, идемпотентно; 200 с отчётом или 202 со scan id | 2 | F4-1 |
| F4-3 | Приоритетный scan-job: очередь, прогресс, кэш, TTL pinned-репозиториев, выживание при rebuild | 2 | F4-2 |
| F4-4 | GitHub API preview для холодного репозитория (stars/forks/issues/contributors) + async полный score | 2 | F4-2 |
| F4-5 | Тесты: long-tail ≤3 с, cold → 202/preview, идемпотентность, pinned после rebuild; бенчмарк | 1 | F4-1…F4-4 |

**Честная семантика:**

- репозиторий активен в окне и есть в long-tail → **200 за 1–3 с**;
- нет событий в окне → **202** + GitHub preview (базовая статистика) + фоновый скан;
- «добавить в индекс» = закрепить репозиторий в top-tier на TTL; если он уже
  в long-tail, промоушен мгновенный, иначе 202 + приоритетный скан.

**DoD:** добавление идемпотентно; warm-репозиторий ≤3 с; cold — preview/202;
pinned переживает пересборку; OpenAPI/README/тесты/бенчмарк.

---

## 5. Кросс-задачи

| ID | Задача | Оценка |
|----|--------|--------|
| X-1 | Обновить OpenAPI и README: новые поля, эндпоинты, коды ошибок | 1 |
| X-2 | Метрики: `index.build.duration`, `index.size`, `index.memory`, `parse.lines/sec`, `scan.latency`, `mcp.llm.latency` | 1–2 |
| X-3 | Приватность: логины по умолчанию, режим hash, лимит выборки, секреты только в env | 1 |
| X-4 | CI: unit/integration офлайн; perf-smoke на синтетическом датасете | 1 |
| X-5 | Документация MCP/Claude Code, extension и on-demand | 1 |

---

## 6. Порядок выполнения и роли

```text
Этап 1 (2–3 дня): F3-1 парсер + F1-1/F1-2 API-поля (параллельно)
Этап 2 (3–4 дня): F3-3 tiered-модель + F1-3 UI + F1-4 тесты
Этап 3 (3–4 дня): F4-1 long-tail + F4-2 add endpoint + F3-4/5/6
Этап 4 (2–3 дня): F2 MCP + Claude (можно параллельно после F1 API)
Этап 5: F4-5 бенчмарки, X-1…X-5, демо
```

Ориентир: **30–40 человеко-дней**; 5 разработчиков — ~2 недели при параллельной
работе. Основной риск — F3-3/F4-1 (память и long-tail), их делает сильный
JVM-разработчик.

| Роль | Зона |
|------|------|
| Dev 1 | F2 MCP/Claude, API-контракты, X-1 |
| Dev 2 | F3 парсер, инкрементальный индекс, F4 long-tail |
| Dev 3 | F3 tiered-модель, память, C4 на 10k |
| Dev 4 | F1 API + extension UI + preview/tests |
| Dev 5 | F3-6/F4-5 бенчмарки, X-2/X-4, Compose/деплой MCP |

---

## 7. Риски и открытые вопросы

| Риск | Митигация |
|------|-----------|
| Long-tail не влезает в память | memory budget + eviction/backpressure; long-tail без логинов и `ActorActivity`; top-n=10k по умолчанию |
| 1–3 с недостижимы для репозитория без событий | GitHub preview <1 с + async полный score; честный 202 |
| LLM путает mock/insufficient | guardrails, передача `mocked`/coverage, fallback на `ExplainTexts` |
| MCP-сканирование зависит от single-instance `ScanService` | один scan-backend или shared store перед масштабированием |
| Приватность логинов | режим hash, лимит выборки, публичные данные |
| C1 по умолчанию mock → «накрутка» не подсвечивается | для демо включать `app.scoring.checks.burst.mode=real`; иначе нейтральный «всплеск» |

**Осталось согласовать:**

1. GitHub preview для холодных репозиториев — включаем в PoC или отдельной фичей?
2. Точный memory budget по умолчанию: 512 МБ, 1 ГБ или 1.5 ГБ на индекс?
3. Claude-модель по умолчанию (`claude-sonnet-4-6` для скорости/цены или Opus)?
4. Показывать ли контрибьюторов и подозрительных аккаунтов в popup или только в sidebar-карточке?

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

Для F3/F4 — отдельный бенчмарк на синтетическом окне: build time, heap,
p95 ответа, размер top-tier/long-tail. Для F2 — тест с fake core/fake LLM и
ручная проверка в Claude Code.
