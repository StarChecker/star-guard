# Star Guard — план core-логики (v1)

> Статус: черновик к ревью команды · Ветка: `feature/core` · Владелец: Dev 3
>
> Основа: командные документы `README.md`, `PLAN.md`, `TASKS.md`, `CHECKS.md`
> (концепция, план по дням, постановки C1–C5 и алгоритм сборки score).

---

## 1. Что такое core и зачем он нужен

`core` — это библиотека (Maven-модуль), в которой живёт **домен и вся логика
оценки подозрительности**: контракты, снимок репозитория, пять проверок C1–C5,
движок сборки итогового `suspicious score` и explain.

Сервисы (`aggregation`, `storage-scoring`, `api`) пользуются core через
публичный Java API и не знают о деталях алгоритмов. Core, в свою очередь,
**не знает о Kafka, Redis, HTTP и Spring Boot runtime** — это позволяет:

- тестировать логику на фикстурах без инфраструктуры (как и требует `TASKS.md`);
- собирать один и тот же score в Streams-приложении и в REST-сервисе;
- менять хранилище (Redis → ClickHouse) без изменения алгоритмов.

```
GH Archive → Producer → Kafka → aggregation (окна) → storage-scoring (Redis)
                                                        │
                                                        │ RepoSnapshotAssembler
                                                        ▼
                                              ┌───────────────────┐
                                              │       core        │
                                              │  RepoSnapshot     │
                                              │  RepoCheck × 5    │
                                              │  ScoringEngine    │  → RepoScore (score, level, explain)
                                              └───────────────────┘
                                                        │
                                                        ▼
                                                   api (REST/SSE) → UI
```

**Границы ответственности:**

| Входит в core | Не входит в core |
|---------------|------------------|
| `GhEvent` — общая схема события (контракт с ingestion/aggregation) | Парсинг `.json.gz` GH Archive |
| `RepoSnapshot`, `StarActor`, `OverlapStats`, `HourProfile` | Сборка `RepoSnapshot` из Redis (`storage-scoring`) |
| Контракт `RepoCheck` / `CheckResult` | Kafka Streams, окна, trending |
| Реализации проверок C1–C5 | REST/OpenAPI/SSE/UI |
| `ScoringEngine`: веса, гейты, уровни, усиление, explain | Хранение истории score (Redis) |
| `scoring.yaml`: загрузка, типизация, валидация | GitHub REST enrichment (stretch, сервисный слой) |
| Утилиты для проверок: `clamp01`, медиана, скользящие окна, Jaccard, хэш актора | Метрики-инфраструктура и Grafana-дашборды (только Micrometer-метрики из core) |

---

## 2. Фичи первой версии

Приоритеты: **Must** — без этого нет MVP; **Should** — делаем, если контракт
стабилен; **Stretch** — не трогаем до feature freeze.

| ID | Фича | Приоритет | Критерий готовности |
|----|------|-----------|---------------------|
| F1 | Доменная модель и контракт (`RepoSnapshot`, `RepoCheck`, `CheckResult`, `RepoScore`) | Must | Контракт смержен в `main` до обеда дня 1; остальные пишут против него |
| F2 | Реестр проверок через Spring DI + включение/веса из `scoring.yaml` | Must | Проверка регистрируется бин-классом, неактивные не вызываются |
| F3 | C1 «Всплеск звёзд» (`BurstCheck`) | Must | Фикстуры `burst_spike` ≥ 0.9, `steady_growth` ≤ 0.2 (владелец — Dev 1) |
| F4 | C2 «Свежие и пустые аккаунты» (`FreshAccountsCheck`) | Must | `fresh_farm` ≥ 0.9, `organic` ≤ 0.1 (Dev 2) |
| F5 | C3 «Дисбаланс звёзды/активность» (`EngagementCheck`) | Must | `star_stuffing` ≥ 0.9, `healthy_lib` ≤ 0.2 (Dev 3) |
| F6 | C4 «Пересечение акторов» (`ActorOverlapCheck`) | Must | `farm` ≥ 0.9, `random_overlap` ≤ 0.1 (Dev 4) |
| F7 | C5 «Суточный профиль» (`CircadianCheck`) | Must | `night_farm` ≥ 0.8, `daytime_normal` ≤ 0.2 (Dev 5) |
| F8 | `ScoringEngine`: взвешенная сборка, гейты, confidence, уровни, правила усиления | Must | Golden-тест на эталонном снапшоте даёт score из `CHECKS.md` §4 |
| F9 | Explain: вклад каждой проверки (`contribution`), `details`, список применённых правил | Must | Ответ API совпадает по форме с примером из `CHECKS.md` §4 |
| F10 | Типизированный `scoring.yaml` + fail-fast валидация на старте | Must | Веса, диапазоны, неизвестные ключи, дубли id — ошибка запуска с понятным текстом |
| F11 | Устойчивость: изоляция падений проверок, детерминизм, Micrometer-метрики | Must | Кривая проверка не ломает остальные; результат не зависит от порядка вызова |
| F12 | Фикстуры + JUnit: golden-тесты движка, тесты утилит, ArchUnit-запрет инфраструктурных зависимостей | Must | `mvn test` зелёный, CI на каждый PR |
| F13 | Hot reload `scoring.yaml` (атомарная подмена конфига) | Should | Меняем порог на диске — следующие оценки используют новое значение без рестарта |
| F14 | Пакетная оценка (`scoreBatch`) для топ-N trending | Should | Один проход, нет пересоздания конфига на каждый репозиторий |

**Stretch (не входит в v1):** MinHash/LSH для C4, перцентильная калибровка C3,
обогащение C2 через GitHub REST, история score, алерты — всё это живёт в
сервисных модулях, не в core.

---

## 3. Архитектура

### 3.1. Место в монорепо

```text
star-guard/
├── core/                  # этот модуль: домен + проверки + движок (без I/O)
├── ingestion/             # producer GH Archive → Kafka
├── aggregation/           # Kafka Streams: окна, trending
├── storage-scoring/       # Redis: агрегаты, RepoSnapshotAssembler, кэш score
├── api/                   # REST/SSE, OpenAPI-маппинг RepoScore
├── ui/                    # минимальная страница
├── infra/                 # compose, Grafana
└── docs/
    ├── core-plan.md       # этот документ
    ├── event-schema.md    # контракт события (день 1)
    └── openapi.yaml       # контракт API (день 1)
```

Артефакт: `ru.itmo.ipkn.starguard:core:0.1.0-SNAPSHOT`.
Зависимости: `api` → `core` (напрямую для DTO), `storage-scoring` → `core`,
`aggregation` → `core` (только `GhEvent`).

### 3.2. Пакеты

```text
ru.itmo.ipkn.starguard.core
├── event      GhEvent — общая схема события (v1)
├── domain     RepoSnapshot, StarActor, OverlapStats, OverlapRef, HourProfile
├── check      RepoCheck, CheckResult, CheckIds
├── checks     BurstCheck, FreshAccountsCheck, EngagementCheck,
│              ActorOverlapCheck, CircadianCheck (+ params-записи каждого)
├── engine     ScoringEngine, ScoringEngineImpl, RepoScore, CheckContribution,
│              ScoreLevel, ScoreAssembler
├── config     ScoringConfig, CheckConfig, CheckParams, ScoringConfigLoader,
│              ScoringConfigHolder, ScoringConfigValidator, CoreConfiguration
└── util       Norm, Series, Jaccard, ActorHasher
```

Зависимости внутри модуля — только вниз: `checks → engine/domain/util`,
`engine → domain/check/config`, `config` не зависит от `checks`.

### 3.3. Ключевые решения

| # | Решение | Обоснование |
|---|---------|-------------|
| AD-1 | Core — библиотека без I/O: никаких Kafka/Redis/HTTP-зависимостей | Проверки тестируются на фикстурах, ArchUnit охраняет границу |
| AD-2 | `RepoCheck.evaluate(RepoSnapshot)` — **чистая функция** без обращения к внешним системам | Контракт из `CHECKS.md` сохраняется; любой snapshot можно воспроизвести в тесте |
| AD-3 | Всё тяжёлое, что требует других репозиториев/Redis, **пре-агрегируется** в `RepoSnapshot` (`OverlapStats`, `List<StarActor>`, `globalHourShare7d`) | C2/C4/C5 остаются чистыми и дешёвыми; дорогие операции — в `storage-scoring` с кэшем и TTL |
| AD-4 | Все числа — в `scoring.yaml`; в коде нет магических констант | Требование `TASKS.md`; калибровка в день 4 без пересборки |
| AD-5 | Движок изолирует падения проверок и работает по одной неизменяемой конфигурации | Ошибка в C4 не должна ронять score; hot reload не должен давать «рваный» результат |
| AD-6 | Окна и профили считаются по **event time** (`created_at`), а не по wall clock | Реплей x50–x100 сжимает реальное время — C1 и C5 иначе сломаются |
| AD-7 | Explain — часть публичного контракта, а не debug-вывод | Демо и UI показывают разбор сигналов |

### 3.4. Поток данных

1. `aggregation` пишет агрегаты в Redis (ключи — в `PLAN.md`, день 1).
2. `RepoSnapshotAssembler` (`storage-scoring`) собирает `RepoSnapshot`:
   - локальные счётчики из `repo:{id}:stats`;
   - ряд по минутам из `repo:{id}:minutes`;
   - акторы и их свежесть из `repo:{id}:actors` + глобального индекса firstSeen;
   - `OverlapStats` — раз в минуту по топ-500 (Redis `SINTERCARD`);
   - глобальный часовой профиль `global:hours7d`;
   - `dataCompleteness` и `missingData`.
3. `ScoringEngine.score(snapshot)` → `RepoScore`.
4. `api` маппит `RepoScore` в JSON и отдаёт в UI/SSE; `storage-scoring` кэширует
   score с TTL (например, 60 с).

---

## 4. Публичный Java API

### 4.1. Доменная модель

```java
public record GhEvent(
        String eventId,          // идемпотентность ingestion
        String type,             // WatchEvent, ForkEvent, PushEvent, IssuesEvent, ...
        long repoId,
        String repoFullName,     // "owner/name"
        long actorId,
        Instant actorCreatedAt,  // nullable: не во всех payload GH Archive есть
        Instant createdAt        // event time
) {}
```

```java
/** Полный снимок репозитория — единственный вход всех проверок. */
public record RepoSnapshot(
        String repoId,
        String fullName,
        Instant capturedAt,

        int stars1h,
        int stars24h,
        int stars7d,
        int[] starsByMinute,          // ровно 1440, старые → новые

        int forks24h,
        int issues24h,
        int pulls24h,
        int pushes24h,

        int uniqueActors24h,
        List<StarActor> actors24h,    // для C2; может быть усечён (см. actorSample)
        OverlapStats overlap,         // для C4; null если ещё не посчитан

        int[] hourHistogram7d,        // ровно 24, UTC
        double[] globalHourShare7d,   // ровно 24, сумма ≈ 1

        double dataCompleteness,      // 0..1
        Set<String> missingData       // имена отсутствующих блоков
) {}
```

```java
/** Актор, поставивший звезду за 24 ч. Поля — вход C2. */
public record StarActor(
        String hash,                  // обезличенный id (ActorHasher)
        Instant accountCreatedAt,     // nullable
        Instant firstSeenInStream,    // nullable
        boolean hasOtherActivity90d   // push/issue/PR за 90 дней
) {}

/** Предвычисленное пересечение с другими репозиториями — вход C4. */
public record OverlapStats(
        double jaccardMax,
        List<OverlapRef> topOverlaps, // топ-3
        double multiShare,
        int actorsAnalyzed,
        int candidatesCompared
) {}

public record OverlapRef(String repoId, String fullName, double jaccard) {}
```

**Правила валидации** (compact-конструкторы, ошибка — `IllegalArgumentException`):
длины массивов, неотрицательность счётчиков, `0 <= dataCompleteness <= 1`,
`fullName` в формате `owner/name`, массивы копируются при создании
(иммутабельность и потокобезопасность).

### 4.2. Контракт проверки

```java
public interface RepoCheck {
    String id();                          // CheckIds.BURST = "burst", ...
    CheckResult evaluate(RepoSnapshot snapshot);
}

public record CheckResult(
        String checkId,
        double score,                     // 0..1 после нормализации
        double confidence,                // 0..1 — полнота данных
        Map<String, Object> details       // для explain; ограниченный набор, без PII
) {}
```

Требования к реализации проверки:

- не мутирует snapshot и не хранит состояния между вызовами;
- не бросает исключения наружу — движок их всё равно изолирует, но правило
  «проверка сама не падает» проверяется тестами;
- при нехватке данных возвращает низкий `confidence`, а не высокий score;
- все пороги получает из своего `params`-record, а не из констант.

```java
public final class CheckIds {
    public static final String BURST = "burst";
    public static final String FRESH_ACCOUNTS = "fresh_accounts";
    public static final String ENGAGEMENT = "engagement";
    public static final String ACTOR_OVERLAP = "actor_overlap";
    public static final String CIRCADIAN = "circadian";
}
```

### 4.3. Движок

```java
public interface ScoringEngine {
    RepoScore score(RepoSnapshot snapshot);
    List<RepoScore> scoreBatch(List<RepoSnapshot> snapshots);   // Should (F14)
}

public record RepoScore(
        String repoId,
        String fullName,
        int score,                        // 0..100
        ScoreLevel level,
        double confidence,                // 0..1 = min по активным проверкам
        boolean insufficientData,         // нет ни одной активной проверки
        List<CheckContribution> checks,
        Instant computedAt,
        Map<String, Object> meta          // rulesApplied, warnings, configHash
) {}

public record CheckContribution(
        String checkId,
        double score,
        double confidence,
        double weight,
        double contribution,              // 100 * w * score / W, 0 для неактивных
        boolean active,
        String error,                     // null, если проверка отработала
        Map<String, Object> details
) {}

public enum ScoreLevel { LOW, MEDIUM, HIGH, CRITICAL }
```

### 4.4. Конфигурация

```java
public record ScoringConfig(
        EngineConfig engine,
        List<CheckConfig> checks,         // порядок = порядок в YAML
        String configHash                 // SHA-256 содержимого файла
) {}

public record EngineConfig(
        double minConfidence,             // 0.5 — порог активности проверки
        AmplificationConfig amplification,
        LevelThresholds levels
) {}

public record CheckConfig(String id, boolean enabled, double weight, Map<String, Object> params) {}
```

Доступ к конфигу — через неизменяемый холдер с атомарной подменой:

```java
@Component
public final class ScoringConfigHolder {
    public ScoringConfig current();
    public <P extends CheckParams> P params(String checkId, Class<P> type); // кэш разбора
    public void reload();               // Should (F13)
}
```

Проверка берёт параметры на каждом вызове через холдер — это оставляет
`evaluate(RepoSnapshot)` стабильным и заодно даёт hot reload.

### 4.5. Утилиты для владельцев проверок

```java
public final class Norm {
    public static double clamp01(double x);
    public static double normalize(double x, double floor, double span); // clamp01((x-floor)/span)
}

public final class Series {
    public static long maxSlidingSum(int[] series, int window);
    public static double median(int[] series);
}

public final class Jaccard {
    public static double of(Set<String> a, Set<String> b);
}

public final class ActorHasher {
    public static String hash(long actorId);  // SHA-256, первые 16 hex-символов
}
```

### 4.6. Пример использования (сервисный слой)

```java
RepoSnapshot snapshot = snapshotAssembler.assemble("owner/repo");  // storage-scoring
RepoScore score = scoringEngine.score(snapshot);                   // core
String json = scoreMapper.toJson(score);                           // api
```

Spring-конфигурация подключается одним бином:

```java
@Configuration
@Import(CoreConfiguration.class)   // даёт ScoringConfigHolder, ScoringEngine, все RepoCheck
class StorageScoringConfig {}
```

---

## 5. JSON-контракт explain

Форма совпадает с примером из `CHECKS.md` §4 (снаке-кейс — ответственность `api`,
core отдаёт camelCase-записи):

```json
{
  "repo_id": "123456",
  "full_name": "owner/repo",
  "score": 72,
  "level": "high",
  "confidence": 0.9,
  "insufficient_data": false,
  "computed_at": "2026-11-10T12:00:00Z",
  "checks": [
    {"id": "burst",          "score": 0.91, "confidence": 0.9, "weight": 0.30,
     "contribution": 27.3, "active": true, "details": {"maxBurst10m": 42, "ratio": 21.0}},
    {"id": "fresh_accounts", "score": 0.66, "confidence": 0.9, "weight": 0.22,
     "contribution": 14.5, "active": true, "details": {"freshShare": 0.7}},
    {"id": "engagement",     "score": 0.80, "confidence": 1.0, "weight": 0.18,
     "contribution": 14.4, "active": true, "details": {"engagement": 0.04}},
    {"id": "actor_overlap",  "score": 0.55, "confidence": 0.9, "weight": 0.22,
     "contribution": 12.1, "active": true, "details": {"jaccardMax": 0.25}},
    {"id": "circadian",      "score": 0.40, "confidence": 0.9, "weight": 0.08,
     "contribution": 3.2,  "active": true, "details": {"kl": 0.78}}
  ],
  "meta": {"rules_applied": ["strong_check"], "warnings": [], "config_hash": "ab12…"}
}
```

HTTP-маппинг и OpenAPI — зона Dev 4: `GET /api/v1/repos/{owner}/{name}/score`
отдаёт `RepoScore` целиком; в элементах `/trending` достаточно
`score`, `level`, `confidence`.

---

## 6. `RepoSnapshot`: поля, источники и потребители

| Поле | Источник (storage-scoring) | Нужно проверке |
|------|----------------------------|----------------|
| `stars1h/24h/7d` | окна Streams в Redis | C1, C3, C5 |
| `starsByMinute[1440]` | минутный ряд за 24 ч | C1 |
| `forks24h/ issues24h/ pulls24h/ pushes24h` | счётчики окна 24 ч | C3 |
| `uniqueActors24h`, `actors24h[]` | set акторов + firstSeen-индекс + история активности 90 д | C2 |
| `overlap` | раз в минуту: `SINTERCARD` по топ-500 + multi-repo индекс | C4 |
| `hourHistogram7d[24]`, `globalHourShare7d[24]` | часовые агрегаты за 7 д | C5 |
| `dataCompleteness`, `missingData` | оценка assembler’а | все (confidence, UI) |

Ограничения, которые фиксируем в контракте:

- `actors24h` может быть усечён до `actorSampleLimit` (например, 5000) —
  `OverlapStats.actorsAnalyzed` и `uniqueActors24h` позволяют проверке понять
  полную выборку;
- `overlap == null` — C4 обязан вернуть `confidence = 0` и не падать;
- `actorCreatedAt` и `firstSeenInStream` — nullable, C2 использует fallback
  из `TASKS.md` §2.

---

## 7. `scoring.yaml`

Единый файл на модуль; путь задаётся свойством
`star-guard.core.scoring-config` (default: `classpath:scoring.yaml`, в compose —
volume). Веса и пороги — из `TASKS.md`, без изменений.

```yaml
engine:
  min_confidence: 0.5
  amplification:
    multi_trigger: {min_checks: 2, check_score_at_least: 0.7, floor: 60}
    strong_check:
      check_ids: [burst, fresh_accounts, actor_overlap]
      check_score_at_least: 0.85
      min_confidence: 0.7
      floor: 70
  levels: {medium: 30, high: 60, critical: 85}

checks:
  burst:
    enabled: true
    weight: 0.30
    ratio_floor: 3.0
    ratio_span: 12.0
    min_burst: 10
    warmup_minutes: 60
  fresh_accounts:
    enabled: true
    weight: 0.22
    new_account_days: 30
    empty_actor_days: 90
    share_floor: 0.3
    share_span: 0.6
    min_actors: 10
  engagement:
    enabled: true
    weight: 0.18
    engagement_floor: 0.2
    min_stars: 20
  actor_overlap:
    enabled: true
    weight: 0.22
    candidate_pool: 500
    jaccard_floor: 0.05
    jaccard_span: 0.45
    multi_repo_threshold: 3
    multi_floor: 0.1
    multi_span: 0.4
    min_actors: 10
  circadian:
    enabled: true
    weight: 0.08
    window_days: 7
    kl_floor: 0.3
    kl_span: 1.2
    min_stars: 50
```

**Валидация на старте (fail-fast, `ScoringConfigException`):**

- каждому `CheckConfig.id` соответствует ровно один зарегистрированный `RepoCheck`;
- веса включённых проверок в `(0, 1]`, сумма всех `weight` ≈ 1.0 (±1e-6);
- `params` каждого check разбираются в свой record (Jackson) и проходят
  `CheckParams.validate()`: диапазоны (например, `ratio_span > 0`), обязательные поля;
- неизвестные ключи в YAML — ошибка (опечатка в пороге не должна молча игнорироваться);
- уровни строго возрастают: `medium < high < critical`;
- дубли id, `null`/NaN — ошибка.

`configHash` (SHA-256 файла) попадает в `RepoScore.meta` — по нему на демо
видно, какой конфигурацией посчитан score.

---

## 8. Алгоритм сборки score (реализация `CHECKS.md` §4)

```text
1. enabled = checks, где config.enabled == true
2. для каждой проверки в порядке YAML:
     try  → CheckResult (обязательно checkId == id; clamp score/confidence в 0..1)
     catch → CheckContribution{active=false, error=…}, метрика failures++, warning
3. active = результаты с confidence >= engine.min_confidence (и без ошибки)
4. если active пуст:
     RepoScore{score=0, level=LOW, confidence=0, insufficientData=true}
5. W     = Σ weight_i по active                     // отключённые выпадают, веса ренормируются
   raw   = Σ weight_i * score_i / W
   score = round(100 * raw), clamp 0..100
6. правила усиления (только повышают, meta.rulesApplied):
     multi_trigger: count(score_i >= 0.7 по active) >= 2            → score = max(score, 60)
     strong_check:  ∃ i ∈ check_ids: score_i >= 0.85 и conf_i >= 0.7 → score = max(score, 70)
7. contribution_i = 100 * weight_i * score_i / W (1 знак после запятой при маппинге)
8. confidence = min(confidence_i по active)
9. level по порогам из конфига
```

Крайние случаи, закреплённые тестами:

- проверка вернула `null` или исключение → неактивна, warning в `meta`;
- `NaN`/`Infinity` в score/confidence → считаем 0;
- проверка вернула чужой `checkId` → `IllegalStateException` на старте (тест
  контракта), в рантайме — отбрасывается;
- отключённая проверка не вызывается вовсе;
- одинаковый snapshot дважды → одинаковый `RepoScore` (кроме `computedAt`);
- score считается детерминированно при параллельных вызовах (stateless).

---

## 9. Надёжность и производительность

- **Stateless.** Движок и проверки не хранят состояния; конфиг — immutable,
  подменяется атомарно. Один экземпляр безопасен для REST и Streams одновременно.
- **Стоимость.** C1 — O(1440); C2 — O(actors); C3 — O(1); C4 — O(1) по
  предвычисленному `OverlapStats`; C5 — O(24). Цель — < 1 мс на snapshot.
- **Нет внешних вызовов в проверках** — значит, нет таймаутов и висящих потоков.
  Всё сетевое (GitHub enrichment) — в сервисном слое, до сборки snapshot.
- **Память.** Массивы копируются на границе записей; `details` ограничен
  несколькими полями (топ-3 пересечения, пиковые часы), без списков акторов.
- **Метрики (Micrometer):**
  `starguard.scoring.duration{id}` (timer), `starguard.scoring.check.failures{id}`
  (counter), `starguard.scoring.insufficient_data` (counter).
- **Приватность.** В `details` — только `ActorHasher.hash`, не логины.

---

## 10. Тестирование

| Уровень | Что проверяем |
|---------|----------------|
| Утилиты | `Norm`, `Series.maxSlidingSum/median`, `Jaccard` — границы, пустые массивы, NaN |
| Проверки | Фикстуры из `TASKS.md` (по 2–3 на проверку) + краевые: мало данных, `overlap == null`, нули |
| Движок | Golden-тесты: эталонный snapshot (пример из `CHECKS.md` §4) → score 72, level high; ренормировка при отключённой проверке; усиления; изоляция исключений; детерминизм |
| Конфиг | Валидация битого YAML, несовпадение id, неизвестные поля, ренормировка весов; загрузка **реального** `scoring.yaml` в CI |
| Архитектура | ArchUnit: `core` не зависит от `kafka`, `redis`, `spring-web`, JDBC |
| Контракт | JSON-снапшот `RepoScore` (explain) — ловит случайные изменения публичного API |

Фикстуры — JSON-файлы в `core/src/test/resources/fixtures/` + загрузчик
`SnapshotFixtures.load("burst_spike.json")`, чтобы фикстуры были читаемы и
переиспользуемы владельцами проверок.

**Definition of Done для core v1:**

- [ ] контракт (`domain`, `check`, `engine`) смержен и зафиксирован в `docs/core-api.md`;
- [ ] 5 проверок зарегистрированы как бины, тесты зелёные;
- [ ] движок выдаёт score/level/confidence/explain по алгоритму §8;
- [ ] `scoring.yaml` валидируется, реальный файл грузится в тесте;
- [ ] ArchUnit и JSON-снапшот зелёные;
- [ ] метрики видны в Grafana;
- [ ] README модуля: как подключить core и добавить новую проверку.

---

## 11. План работ по дням

Соответствует `PLAN.md`; core не блокирует остальные зоны после дня 1.

| День | Что делаем | Артефакт |
|------|-----------|----------|
| **День 1, утро** | Ревью этого плана, фиксация контракта, каркас модуля `core`, `domain` + `check` + `engine`-сигнатуры, `scoring.yaml` с весами | PR с контрактом в `main` до обеда; все пишут против него |
| **День 1, вечер** | `ScoringConfigLoader/Holder/Validator`, заглушка движка, `CoreConfiguration`, фикстуры-загрузчик | Pipeline из `PLAN.md` компилируется с реальным core |
| **День 2** | `ScoringEngineImpl` v1 (веса, гейты, уровни), утилиты, скелеты C1–C5 у владельцев, golden-фикстуры | Движок считает score на фикстурах; каждая проверка демонстрируется на «плохой/здоровой» фикстуре |
| **День 3** | C1–C5 на реальных данных, explain + усиления финально, метрики, совместная калибровка порогов | Score с разбором отдаётся через сервис; пороги откалиброваны |
| **День 4** | Feature freeze: только калибровка весов, багфиксы, README/`docs/core-api.md` | Стабильный контракт, демо-примеры «накрученный vs здоровый» |

---

## 12. Риски и запасные варианты

| Риск | Решение |
|------|---------|
| Владельцы проверок ждут контракт | Контракт фиксируется до обеда дня 1; этот план — предмет ревью утром; дальше — только совместимые изменения через PR |
| Нет `actor_created_at` в payload | Прокси из `TASKS.md` §2: firstSeen в потоке + отсутствие активности 90 д; проверяем на реальном дампе в день 1 |
| C4 дорогой | Тяжёлая часть — в `storage-scoring` (топ-500, раз в минуту, `SINTERCARD`); core видит только `OverlapStats` |
| Реплей x100 ломает окна | Все окна и профили — по `created_at` (event time), это зафиксировано в AD-6 |
| Веса «поехали» после отключения проверки | Движок ренормирует по активным весам; тест на golden-снапшоте |
| Пороги разъезжаются с документацией | Типизированные params + валидация + CI-тест загрузки реального `scoring.yaml` |
| Hot reload даёт «рваный» score | Снапшот конфига immutable, подмена атомарная; score всегда считается одной версией конфига (hash в meta) |
| Фикстуры устаревают | Golden-тесты в CI на каждый PR, владелец проверки отвечает за свои фикстуры |

---

## 13. Открытые вопросы к команде

1. **groupId/package**: предлагаю `ru.itmo.ipkn.starguard` — подтвердить до создания модуля.
2. **Поставщики предвычисленных блоков**: кто и с какой частотой пишет
   `OverlapStats` (Dev 4) и `globalHourShare7d` (Dev 2/Dev 3) — нужно согласовать
   в день 1 вместе с ключами Redis.
3. **Кэш score**: TTL и место хранения — `storage-scoring` (Dev 3), в core не входит.
4. **Hot reload**: нужен ли на демо (F13) или достаточно рестарта — влияет на приоритет Should.
5. **Actor hash**: длина/соль для `ActorHasher`; достаточно ли первых 16 hex-символов SHA-256.
6. **Actor sample limit** для `actors24h` (5000?) — согласовать с C2/C4 и объёмом памяти на топ-репозиториях.

---

## 14. Политика изменения контракта

- `domain`, `check`, `engine` — **стабильный публичный API** после дня 1.
- Изменение подписи/полей — только PR с ревью владельца контракта (Dev 3) и
  сообщением в общий чат; ломающее изменение — синхронно со всеми владельцами проверок.
- Добавление новой проверки не меняет контракт: новый `RepoCheck`-бин +
  секция в `scoring.yaml` + фикстуры.
- `details` каждой проверки — её зона ответственности; форма фиксируется в
  `docs/core-api.md` по мере готовности проверок.
