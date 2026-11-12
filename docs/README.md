# Документация Star Guard

Краткое описание проекта и сборка — в [README](../README.md).

## Как это устроено

- [reference.md](reference.md) — технический справочник: API, параметры, конфигурация,
  проверки C1–C4 с формулами, метрики, ограничения PoC (прежний README репозитория)
- [heuristics.md](heuristics.md) — какие признаки накрутки проверяем, почему и где они ошибаются
- [star-data.md](star-data.md) — откуда берутся данные о звёздах: история GitHub и трекер
- [top-1000-index.md](top-1000-index.md) — индекс топ-1000 по GH Archive
- [account-pool.md](account-pool.md) — пул подозрительных аккаунтов
- [openapi.yaml](openapi.yaml) — контракт API
- [architecture/](architecture) — схемы: [обзор](architecture/01-overview.html),
  [запрос](architecture/02-request-sequence.html), [сборка индекса](architecture/03-index-build.html),
  [оценка](architecture/04-scoring.html), [пул аккаунтов](architecture/05-account-pool.html),
  [тестовые прогоны](architecture/06-test-runs.html); опубликованы на
  [GitHub Pages](https://starchecker.github.io/star-guard/architecture/01-overview.html)

## Проверка

- [live-verification.md](live-verification.md) — прогон на реальных данных 12.11.2026
- [poc-dod.md](poc-dod.md) — Definition of Done PoC по веткам

## План и ход работ

- [core-plan.md](core-plan.md) — исходный план core PoC
- [plan-next-features.md](plan-next-features.md) — план следующих фич
- [task-board.md](task-board.md) — доска задач
- [feature-map.md](feature-map.md) — карта выполненных фич
- отчёты по задачам: [F3-1 потоковый парсер](f3-1-streaming-parser.md),
  [F3-2 почасовые агрегаты](f3-2-hourly-aggregates.md)
