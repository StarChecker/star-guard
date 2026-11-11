# F4-2. Добавить / закрепить репозиторий в индексе — отчёт

> Задача [F4-2](task-board.md) из [плана](plan-next-features.md):
> `POST /api/v1/index/repos` / `PUT /api/v1/index/repos/{owner}/{name}`.
> Ветка `feat/f4-2-index-repos-endpoint` поверх [F4-1](f4-1-long-tail.md).

## Поведение

| Ситуация | Ответ |
|----------|-------|
| У репозитория есть события в окне (он в топе или в long-tail) | **200** + отчёт; репозиторий сразу переносится в топ текущего снимка (`source.tier = top`) |
| Событий в окне нет | **202** + скан (`ScanDto`), `Location: /api/v1/scans/{id}`; источник — история GitHub при токене, иначе GH Archive за `max-window-hours` (шире окна индекса) |
| Повторный запрос | то же самое: закрепление не дублируется, `ScanService` возвращает тот же скан |
| Реестр закреплённых заполнен (`app.index.pins.max-pinned`, по умолчанию 1000) | **429** `PIN_LIMIT_REACHED` |
| Индекс строится / отключён (fixture-режим) | **503** `INDEX_NOT_READY` |

`PUT` и `POST` (тело `{owner, name, hours?}`) ведут себя одинаково.

**Закрепление переживает пересборку:** сборщик добавляет закреплённые репозитории в топ
при каждой сборке, пока у них есть события в окне, даже если по звёздам они в топ-N не
проходят. В списке `GET /api/v1/index/repos` у них `pinned: true`.

## Код

- `index/PinnedRepos` — реестр закреплённых (в памяти, без учёта регистра, с лимитом);
- `index/IndexAdmission` — закрепить и перенести из long-tail в топ;
- `RepoIndex.promote` — атомарный перенос (CAS по снимку: при параллельной подмене
  снимка перенос повторяется на новом), `RepoIndexSnapshot.withRepo`;
- `RepoIndexBuilder` — закреплённые с событиями в окне попадают в топ;
- `api/IndexAdmissionController` — `PUT/POST`, 200/202/429/503;
  `RepoReportMapper.toDto(ScanJob)` теперь общий для `/scans` и добавления в индекс.

## Что оставлено для F4-3

TTL закрепления, приоритетная очередь сканов и прогресс — задача F4-3 (Dev 3): реестр
сейчас без срока жизни, скан идёт через обычный `ScanService` (2 воркера, кэш 30 минут).

## Проверка

- `IndexAdmissionIntegrationTest` (HTTP, настоящий индекс на поддельном архиве):
  репозиторий из long-tail → 200 и `tier = top`, повтор идемпотентен, `pinned: true`
  в списке, после пересборки по-прежнему в топе; репозиторий без событий → 202,
  `Location`, скан за 24 часа;
- `IndexAdmissionControllerTest`: 200/202/429/503/400 для `PUT` и `POST`;
- `IndexAdmissionTest`, `PinnedReposTest`, `RepoIndexBuilderTest`
  (`pinnedRepositoriesStayInTopWhileTheyHaveEvents`).
