# Star Guard — карта выполненных фич

Состояние на **2026-11-10**, ветка `main` = `2e294e3`.
Источник статусов — merge-коммиты и PR-рефы GitHub (`refs/pull/1..10`),
локальный прогон `mvn clean test` на JDK 25: **160 тестов, 0 ошибок**.

## 1. Статус PR и веток

| PR | Ветка | Что влито | Merge-коммит | Статус |
|----|-------|-----------|--------------|--------|
| #1 | `feature/core` | ядро PoC | `413e37c` | ✅ merged |
| #2/#4 | `feat/c3-engagement` → `feature/core` | реальная C3 | `7e4ce26` / `27ed090` | ✅ merged |
| #3 | `feat/top-1000-index` | индекс топ-1000 + API | `5c41713` | ✅ merged |
| #5 | `feat/c4-actor-overlap` | реальная C4 + индекс звёзд | `f37e7cf` | ✅ merged |
| #6 | `feat/c2-fresh-accounts` | реальная C2 | `3ed8f29` | ✅ merged |
| #7 | `feat/ux-extension` | API/UX для расширения | `2e294e3` | ✅ merged |
| #8 | `feat/c1-burst` | реальная C1 | `81345f9` | ✅ merged |
| #9 | `fix/swagger-server-url` | Swagger host + Compose | `0fd0883` | ✅ merged |
| #10 | `feat/c2-followups` | C2 в индексе, GitHub API, окно | `38ce8da` | ✅ merged |
| — | `feat/ux-extension` (tip `466926a`) | скан 24 ч + текст «нет событий» в расширении | — | ⚠️ commit есть, PR/merge нет |

Дополнительно на `main` есть прямой коммит `7d3f215` — Dockerfile и Compose.
Ветка `feature/core` (`27ed090`) содержит только ядро + C3; актуальный источник —
`main`. Локальная `feat/top-1000-index` осталась на `32422cd` (PR #3), но `main`
уже содержит follow-up изменения C4/C2/UX.

## 2. Карта фич

### 2.1. Ядро и данные

| Фича | Ветка/PR | Что сделано | Ключевые артефакты |
|------|----------|-------------|--------------------|
| Домен и контракт scoring | #1 | `RepoSnapshot`, `RepoCheck`, `CheckResult`, `RepoScore`, `ScoreLevel` | `domain/`, `scoring/` |
| Клиент GH Archive | #1 | Feign + HC5, streaming gzip/NDJSON, фильтр по репозиторию, ретраи, кэш `(repo, hour)` | `archive/HttpGhArchiveClient`, `GhArchiveFeignClient` |
| Статистика | #1 | счётчики по типам, уникальные акторы, `starsByMinute`, `starsByHour`, пик 10 минут | `stats/StatsAggregator` |
| Движок score | #1 | веса, confidence-гейт, ренормировка, усиления, уровни, explain | `scoring/DefaultScoringEngine` |
| REST + OpenAPI | #1 | `GET /api/v1/repos/{owner}/{name}`, ProblemDetail, Swagger | `api/RepoController`, `OpenApiConfig` |
| Fixture-режим | #1 | офлайн-фикстуры, сдвиг в текущее окно, мгновенное демо | `FixtureGhArchiveClient` |
| Фиксированный конец окна | #10 | `app.archive.window-end` для демо на реальных дампах | `ArchiveProperties`, `TimeWindowCalculator` |
| CI/CD | #1 | CI на JDK 25, Maven cache, release по тегу | `.github/workflows/ci.yml`, `release.yml` |

### 2.2. Проверки накрутки

| Проверка | Ветка/PR | Статус | Что сделано |
|----------|----------|--------|-------------|
| **C1 «Всплеск звёзд»** | `feat/c1-burst` (#8) | ✅ real | `BurstCheck` по `starsByMinute`, median-baseline, ratio, gate, warmup/confidence; `scoring.yaml`, фикстуры `burst_spike` / `steady_growth` / `cold_start` |
| **C2 «Свежие и пустые аккаунты»** | `feat/c2-fresh-accounts` (#6) + `c2-followups` (#10) | ✅ real | `ActorProfile`, `FreshAccountsCheck`, coverage/confidence; профили акторов в индексе, дата аккаунта из GitHub API (`app.github.*`); фикстуры `fresh-farm` / `organic` / `no-actor-data` |
| **C3 «Дисбаланс звёзды/активность»** | `feat/c3-engagement` (#2/#4) | ✅ real | `EngagementCheck`, `CheckMode`, `EngagementProperties`, фикстуры `star-stuffing` / `healthy-lib` |
| **C4 «Пересечение акторов»** | `feat/c4-actor-overlap` (#5) | ✅ real | `StarIndex` (actor→repos), `ActorOverlapCheck`, Jaccard/multiShare, индекс звёзд в том же проходе; фикстуры `farm-target` / `organic-app` |
| **C5 «Суточный профиль»** | stretch | ⬜ mock | остаётся мок-проверкой; реальная KL-дивергенция не реализована |

Все реальные проверки включаются через `app.scoring.mode=real` или точечно
`app.scoring.checks.<id>.mode=real`; по умолчанию `mock` — демо-профили стабильны.

### 2.3. Индекс и ускорение

| Фича | PR | Статус | Что сделано |
|------|----|--------|-------------|
| Индекс топ-1000 | #3 | ✅ | фоновый двухпроходный `RepoIndexBuilder`, `RepoIndexSnapshot`, per-hour агрегаты, атомарная подмена, composer подокна |
| Быстрый путь запроса | #3 | ✅ | `IndexRepoDataProvider`: O(1) lookup вместо скачивания дампов; `hours` ограничен окном индекса |
| Список доступных репозиториев | #3 | ✅ | `GET /api/v1/index/repos?limit=1000` |
| Ошибки индекса | #3 | ✅ | `404 REPO_NOT_INDEXED`, `503 INDEX_NOT_READY`, readiness после прогрева |
| Индекс звёзд для C4 | #5 | ✅ | `StarIndex` по WatchEvent, bounded memory (`max-pairs-per-hour`), кэш часов |
| Профили акторов в индексе | #10 | ✅ | C2 получает `ActorProfile` из индекса, не только из прямого скана |
| Фиксированное окно | #10 | ✅ | индекс не пересобирается по cron при `window-end` |

### 2.4. API, UX и расширение

| Фича | PR | Что сделано |
|------|----|-------------|
| Отчёт для UI | #1/#7 | `score.verdict`, `checks[].title/summary`, `stars_timeline` (10/20/30 мин, `suspicious`, `estimated`), нейтральные тексты |
| Batch | #7 | `POST /api/v1/repos/batch` — сводки до 50 репозиториев (только индекс) |
| Бейдж | #7 | `GET /api/v1/badge/{owner}/{name}` для shields.io |
| Скан по запросу | #7 | `POST /api/v1/scans` + `GET /api/v1/scans/{id}` для репозитория вне индекса, прогресс по часам |
| Chrome-расширение | #7 | карточка у Star, график, точки в списках, скан по кнопке, preview-состояния |
| Окно анализа в расширении | — (ветка `docs/next-features-plan`, не влито) | в заголовке карточки и в настройках — любое окно 1–24 ч вместо пресетов 3/6/12/24; выбор сохраняется в `chrome.storage.sync`; если окно больше `app.index.window-hours`, карточка предлагает прямой скан GH Archive (`POST /api/v1/scans`) |
| GitHub Action | #7 | проверка новых зависимостей PR (npm/pip) с комментарием |
| Swagger host | #9 | Swagger использует host запроса; Compose включает real scoring |
| Docker/Compose | `7d3f215` | `Dockerfile`, `compose.yaml`, непривилегированный пользователь, healthcheck, env-конфиг |

### 2.5. Документация и качество

- `docs/core-plan.md`, `docs/top-1000-index.md`, `docs/poc-dod.md`;
- `docs/architecture/01-overview.html`, `02-request-sequence.html`,
  `03-index-build.html`, `04-scoring.html` (PR #10);
- `docs/openapi.yaml` — актуальный контракт;
- тесты: **160** на JDK 25 (парсер, агрегатор, движок, C1–C4, индекс,
  GitHub API enrichment, scan/batch/badge/timeline, контроллеры, OpenAPI);
- CI: `.github/workflows/ci.yml`; релиз — `.github/workflows/release.yml`.

## 3. API-карта

| Метод | Путь | Назначение |
|-------|------|------------|
| GET | `/api/v1/repos/{owner}/{name}` | статистика + score + explain + таймлайн |
| POST | `/api/v1/repos/batch` | сводки до 50 репозиториев (индекс) |
| GET | `/api/v1/badge/{owner}/{name}` | JSON для shields.io |
| POST | `/api/v1/scans` | фоновый скан репозитория вне индекса |
| GET | `/api/v1/scans/{id}` | статус/результат скана |
| GET | `/api/v1/index/repos` | доступные репозитории топ-N |
| GET | `/v3/api-docs`, `/v3/api-docs.yaml` | OpenAPI |
| GET | `/swagger-ui.html` | Swagger UI |
| GET | `/actuator/health` | health |

## 4. Что по итогу

- **PoC закрыт по основному сценарию:** ядро + реальные C1–C4 + индекс
  топ-1000 + API для расширения + Docker/Compose + CI.
- **В `main` влиты все PR #1–#10**, `main` = `2e294e3`; локально 160 тестов
  зелёные. C5 остаётся мок/stretch.
- **Незамёрженный хвост:** `feat/ux-extension` tip `466926a` — улучшение
  расширения (скан за 24 ч и понятный текст при пустом окне). Нужен PR/merge.
- **В работе (ветка `docs/next-features-plan`, PR ещё нет):** выбор произвольного
  окна анализа 1–24 ч прямо в карточке и в настройках расширения; при окне больше
  окна индекса — состояние `out_of_window` с кнопкой прямого скана. Ограничение
  `hours ≤ app.index.window-hours` из §2.3 не меняется.
- **DoD в `docs/poc-dod.md` устарел** (C1/C4 помечены «не начато», индекс —
  «в работе»); актуальная картина — в этом документе.
- **Реальный GH Archive** из текущего окружения недоступен (прокси 407);
  проверка HTTP-режима и прогрев индекса выполнены локально на фикстурах/WireMock,
  для реального демо используйте `app.archive.window-end` (см. README — в 2026
  в GH Archive мало событий/нет пушей, берите дату до мая 2025).
