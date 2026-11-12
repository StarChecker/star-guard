# Star Guard — доска задач

Статусы задач из [docs/plan-next-features.md](plan-next-features.md).
Один взгляд — кто что делает и что уже готово.

## Легенда

| Статус | Значение |
|--------|----------|
| ⬜ не взят | задачу никто не начал |
| 🟡 в работе | исполнитель взял задачу, указал `@handle` и ветку |
| ✅ выполнено | код в `main`, тесты/DoD закрыты, PR смержен |

## Как отметиться

1. Выбери задачу со статусом ⬜.
2. Поставь **🟡 в работе**, впиши свой Telegram `@handle` и ветку — через PR
   в этот файл (или сообщи в командный чат, если PR ещё нет).
3. Веди работу в ветке `feat/<task-id>-<кратко>`; PR — в `main` (или в базовую
   ветку, если команда ещё использует `feature/core`).
4. После мержа PR и зелёного `mvn test` поставь **✅ выполнено** и добавь ссылку
   на PR/коммит.
5. Не меняй чужие строки без согласования с исполнителем — иначе конфликты в PR.

> Если задача стоит в зависимости (`Dep`), её можно брать только после
> выполнения зависимостей. Если что-то блокирует — напиши в чат и оставь 🟡.

## Сводка

- Всего задач: **28**
- ⬜ не взят: **18**
- 🟡 в работе: **6**
- ✅ выполнено: **4**

## F1. Расширение: аккаунты, интервалы всплеска, контрибьюторы

| ID | Задача | Владелец | Оценка | Dep | Статус | Ветка / PR |
|----|--------|----------|--------|-----|--------|------------|
| F1-1 | API `spike_intervals[]` в `StarTimelineDto` | Dev 4 · `@nenerower` | 1–2 | — | ⬜ | — |
| F1-2 | API `suspicious_actors[]` (login/причины) + `contributors` | Dev 4 · `@nenerower` | 3 | C2/C4 real, F3-3 | ⬜ | — |
| F1-3 | Popup/sidebar UI: интервал, топ-5 аккаунтов + «ещё», плитки | Dev 4 · `@nenerower` | 3 | F1-1, F1-2 | ⬜ | — |
| F1-4 | Preview-фикстуры, тесты, OpenAPI, README | Dev 4 · `@nenerower` | 2 | F1-1…F1-3 | ⬜ | — |

## F2. MCP-сервер под Claude Code (stdio, без внешних LLM)

| ID | Задача | Владелец | Оценка | Dep | Статус | Ветка / PR |
|----|--------|----------|--------|-----|--------|------------|
| F2-1 | Каркас `mcp/` на TypeScript + MCP SDK (stdio) | Dev 1 · `@h0o0p0e` | 2 | — | ⬜ | — |
| F2-2 | Инструменты `assess_dependency`, report/scan/batch/index/badge | Dev 1 · `@h0o0p0e` | 2 | F2-1 | ⬜ | — |
| F2-3 | Детерминированная трактовка по `level`/`verdict`/порогам | Dev 1 · `@h0o0p0e` | 1 | F2-2 | ⬜ | — |
| F2-4 | Guardrails: `mocked`/`insufficient_data`/покрытие, дисклеймер | Dev 1 · `@h0o0p0e` | 1 | F2-3 | ⬜ | — |
| F2-5 | Конфиг Claude Code, Compose, тесты с fake core | Dev 1 · `@h0o0p0e` | 1–2 | F2-4 | ⬜ | — |

## F3. Парсинг, 10k top-tier и long-tail без OOM

| ID | Задача | Владелец | Оценка | Dep | Статус | Ветка / PR |
|----|--------|----------|--------|-----|--------|------------|
| F3-1 | Streaming-парсер, lightweight pass 1, early repo filter | Dev 2 · `@Ar6yZxZ` | 3 | — | 🟡 | `feat/f3-1-streaming-parser` · [PR #13](https://github.com/StarChecker/star-guard/pull/13) на ревью · [отчёт](f3-1-streaming-parser.md) |
| F3-2 | Почасовые compact-агрегаты, инкрементальный сдвиг окна | Dev 2 · `@Ar6yZxZ` | 3 | F3-1 | 🟡 | `feat/f3-2-hourly-aggregates` · [PR #14](https://github.com/StarChecker/star-guard/pull/14) на ревью (поверх PR #13) |
| F3-3 | Tiered-модель top-10k + detail-1k + long-tail, primitive arrays | Dev 3 · `@mpstrkv` | 4 | F3-2 | ⬜ | — |
| F3-4 | Precomputed rank/totalStars, read-only minute series, дедуп | Dev 3 · `@mpstrkv` | 2 | F3-3 | ⬜ | — |
| F3-5 | C4 на 10k: инвертированный actor→repos / overlaps | Dev 3 · `@mpstrkv` | 2 | F3-3 | ⬜ | — |
| F3-6 | Memory budget 1 ГБ, JVM `-Xmx1280m`, бенчмарк | Dev 5 · `@writenowme` | 2 | F3-3 | 🟡 | [PR #17](https://github.com/StarChecker/star-guard/pull/17): бюджет `app.index.memory-budget-mb`, контроль heap после сборки, `-Xmx1280m`, бенчмарк; eviction — после F3-3 |

## F4. Произвольный репозиторий: long-tail и 1–3 с

| ID | Задача | Владелец | Оценка | Dep | Статус | Ветка / PR |
|----|--------|----------|--------|-----|--------|------------|
| F4-1 | Long-tail индекс всех репозиториев окна | Dev 2 · `@Ar6yZxZ` | 4 | F3-3 | 🟡 | `feat/f4-1-long-tail-index` · [PR #18](https://github.com/StarChecker/star-guard/pull/18) на ревью (поверх #14): long-tail из `HourDigest`, без ожидания F3-3 — формат хранения потом меняет F3-3 |
| F4-2 | `POST/PUT /api/v1/index/repos`: добавить/закрепить | Dev 2 · `@Ar6yZxZ` | 2 | F4-1 | 🟡 | `feat/f4-2-index-repos-endpoint` · [PR #19](https://github.com/StarChecker/star-guard/pull/19) на ревью (поверх #18); TTL и приоритетный скан — F4-3 |
| F4-3 | Приоритетный scan-job, TTL pinned, выживание при rebuild | Dev 3 · `@mpstrkv` | 2 | F4-2 | ⬜ | — |
| F4-4 | Тесты ≤3 с, cold 202, идемпотентность, benchmark | Dev 5 · `@writenowme` | 1 | F4-1…F4-3 | ⬜ | — |

## F5. GitHub preview для холодных репозиториев

| ID | Задача | Владелец | Оценка | Dep | Статус | Ветка / PR |
|----|--------|----------|--------|-----|--------|------------|
| F5-1 | GitHub-клиент stats/forks/issues/contributors, кэш | Dev 5 · `@writenowme` | 2 | — | ✅ | REST-клиент + кэш + пауза по rate limit — [PR #16](https://github.com/StarChecker/star-guard/pull/16); история GraphQL — [PR #11](https://github.com/StarChecker/star-guard/pull/11) |
| F5-2 | `GET /api/v1/preview/{owner}/{name}` | Dev 5 · `@writenowme` | 1 | F5-1 | ✅ | `source=github`, `partial=true` — [PR #16](https://github.com/StarChecker/star-guard/pull/16) |
| F5-3 | Extension/MCP: preview в состоянии «нет событий» | Dev 4 · `@nenerower` | 1 | F5-2 | ⬜ | — |
| F5-4 | WireMock-тесты, OpenAPI, документация | Dev 5 · `@writenowme` | 1 | F5-1…F5-3 | ✅ | `GithubPreviewIntegrationTest`, OpenAPI, README — [PR #16](https://github.com/StarChecker/star-guard/pull/16) |

## X. Кросс-задачи

| ID | Задача | Владелец | Оценка | Dep | Статус | Ветка / PR |
|----|--------|----------|--------|-----|--------|------------|
| X-1 | OpenAPI/README: поля, эндпоинты, коды ошибок | Dev 1 · `@h0o0p0e` | 1 | F1–F5 | ⬜ | — |
| X-2 | Метрики index/parse/scan/mcp | Dev 5 · `@writenowme` | 1–2 | F3, F4 | 🟡 | [PR #17](https://github.com/StarChecker/star-guard/pull/17): index/scan + `/actuator/prometheus`; `feat/x2-parse-metrics`: `starguard_parse_*` (строки/с, байты, ошибки, часы); `mcp.tool.latency` — после F2 |
| X-3 | Приватность логинов/hash, MCP без внешних запросов | Dev 1 · `@h0o0p0e` | 1 | F1, F2 | ⬜ | — |
| X-4 | CI: unit/integration офлайн + perf-smoke | Dev 5 · `@writenowme` | 1 | F3 | ✅ | [PR #17](https://github.com/StarChecker/star-guard/pull/17): `PerfSmokeTest`, отчёт в сводке CI |
| X-5 | Документация MCP/Claude Code, extension, on-demand | Dev 1 · `@h0o0p0e` | 1 | F2, F4 | ⬜ | — |

---

## Примечания

- Оценки — человеко-дни, из [плана](plan-next-features.md); при взятии задачи
  можно уточнить в PR.
- «Выполнено» = PR смержен, тесты зелёные, DoD задачи закрыт; не просто
  «код написан».
- Для GitHub Projects/Issues можно позже импортировать этот список: ID задачи
  использовать как заголовок issue, статус — как поле Status.
