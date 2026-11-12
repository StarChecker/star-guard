package ru.itmo.ipkn.starguard.core.api;

import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.scoring.CheckContribution;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Человекочитаемые тексты для explain: название проверки, короткая причина и общий вердикт.
 * Одни и те же формулировки видят расширение, бейдж и презентация.
 * Тон нейтральный: «признаки накрутки», а не «накручено».
 */
public final class ExplainTexts {

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("suspect_accounts", "Подозрительные аккаунты из пула"),
            Map.entry(CheckIds.BURST, "Всплески звёзд"),
            Map.entry(CheckIds.FRESH_ACCOUNTS, "Свежие и пустые аккаунты"),
            Map.entry(CheckIds.ENGAGEMENT, "Звёзды без активности"),
            Map.entry(CheckIds.ACTOR_OVERLAP, "Пересечение аккаунтов"),
            Map.entry(CheckIds.CIRCADIAN, "Суточный ритм"),
            Map.entry(CheckIds.DAILY_BURST, "Всплески звёзд по дням"),
            Map.entry(CheckIds.DEV_ACTIVITY, "Звёзды без разработки"),
            Map.entry(CheckIds.FORK_RATIO, "Аномально много форков"),
            Map.entry(CheckIds.INTRADAY_BURST, "Всплески внутри дня"),
            Map.entry(CheckIds.HOURLY_PROFILE, "Часы звёзд"),
            Map.entry(CheckIds.STAR_DROPS, "Пропавшие звёзды"));

    private ExplainTexts() {
    }

    public static String title(String checkId) {
        return TITLES.getOrDefault(checkId, checkId);
    }

    public static String summary(CheckContribution check, RepoStats stats) {
        Map<String, Object> d = check.details();
        if (check.error() != null) {
            return "Проверка завершилась с ошибкой и не участвует в оценке.";
        }
        if (Boolean.TRUE.equals(d.get("mocked"))) {
            return "Демо-значение: реальная проверка ещё не подключена.";
        }
        String inactive = inactiveReason(d);
        if (inactive != null) {
            return inactive;
        }
        return switch (check.checkId()) {
            case "suspect_accounts" -> "Совпадений с пулом: %d. Проверены последние 100 звёзд каждого аккаунта; отсутствие совпадений не снижает риск. Это признаки, а не доказательство накрутки."
                    .formatted(num(d, "matched_accounts"));
            case CheckIds.BURST -> "Пик — %d %s за 10 минут.".formatted(
                    stats.peakStars10m(), stars(stats.peakStars10m()));
            case CheckIds.FRESH_ACCOUNTS -> "%d из %d %s — от аккаунтов моложе %d дн. или без активности (%s)."
                    .formatted(num(d, "fresh_actors"), num(d, "star_actors"), stars(num(d, "star_actors")),
                            num(d, "new_account_days"), percent(dbl(d, "fresh_share")));
            case CheckIds.ENGAGEMENT -> "На %d %s — %d %s (форки, issues, PR, пуши)."
                    .formatted(num(d, "stars"), stars(num(d, "stars")),
                            num(d, "activity"), actions(num(d, "activity")));
            case CheckIds.ACTOR_OVERLAP -> overlapSummary(d);
            case CheckIds.DAILY_BURST -> "Самый резкий день %s: %d %s при обычных %s в день (x%s), таких дней в окне: %d."
                    .formatted(d.get("best_day"), num(d, "best_stars"), stars(num(d, "best_stars")),
                            decimal(dbl(d, "baseline")), decimal(dbl(d, "ratio")), num(d, "burst_day_count"));
            case CheckIds.DEV_ACTIVITY -> devActivitySummary(d);
            case CheckIds.FORK_RATIO -> "%d форков на %d %s за окно (%s)."
                    .formatted(num(d, "forks"), num(d, "stars"), stars(num(d, "stars")),
                            percent(dbl(d, "forks_per_star")));
            case CheckIds.INTRADAY_BURST -> "Пик: %d %s за 10 минут, это x%s к обычному темпу."
                    .formatted(num(d, "max_burst_10m"), stars(num(d, "max_burst_10m")), decimal(dbl(d, "ratio")));
            case CheckIds.HOURLY_PROFILE -> "Звёзды ставят в нетипичные для GitHub часы: больше всего в %s UTC (D = %s)."
                    .formatted(hours(d.get("peak_hours_utc")), decimal(dbl(d, "kl_divergence")));
            case CheckIds.STAR_DROPS -> "За время наблюдения пропало %d %s при %d новых (%s)."
                    .formatted(num(d, "dropped"), stars(num(d, "dropped")), num(d, "gained"),
                            percent(dbl(d, "drop_share")));
            default -> "Оценка %s.".formatted(percent(check.score()));
        };
    }

    public static String verdict(RepoScore score) {
        if (score.insufficientData()) {
            return "Недостаточно данных, чтобы делать выводы.";
        }
        String base = switch (score.level()) {
            case LOW -> "Признаков накрутки не найдено";
            case MEDIUM -> "Есть отдельные признаки накрутки";
            case HIGH -> "Заметные признаки накрутки";
            case CRITICAL -> "Сильные признаки накрутки";
        };
        Optional<CheckContribution> top = score.checks().stream()
                .filter(CheckContribution::active)
                .filter(check -> check.contribution() > 0)
                .max(Comparator.comparingDouble(CheckContribution::contribution));
        if (score.level().ordinal() == 0 || top.isEmpty()) {
            return base + ".";
        }
        return base + ": главный сигнал — «" + title(top.get().checkId()).toLowerCase(Locale.ROOT) + "».";
    }

    private static String overlapSummary(Map<String, Object> d) {
        int actors = num(d, "star_actors");
        int multi = num(d, "multi_repo_actors");
        String text = "%d из %d отметивших репозиторий поставили звёзды ещё ≥%d репозиториям за окно."
                .formatted(multi, actors, num(d, "multi_repo_threshold") - 1);
        Object top = d.get("top_overlaps");
        if (top instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> first) {
            double jaccard = first.get("jaccard") instanceof Number n ? n.doubleValue() : 0.0;
            if (jaccard >= 0.05) {
                text += " Сильнее всего совпадает аудитория %s (%s).".formatted(first.get("repo"), percent(jaccard));
            }
        }
        return text;
    }

    private static String inactiveReason(Map<String, Object> d) {
        Object reason = d.get("reason");
        if (reason == null) {
            return null;
        }
        return switch (reason.toString()) {
            case "pool_empty" -> "В пуле пока нет актуальных подозрительных аккаунтов. Проверка не снижает риск.";
            case "no_pool_matches" -> "Совпадений с неполной выборкой пула не найдено. Это не подтверждает органичность звёзд.";
            case "too_few_stars", "too_few_star_actors", "no_star_actors" ->
                    "Слишком мало звёзд за окно — проверка не участвует в оценке.";
            case "no_cross_repo_index" -> "Нет индекса звёзд по другим репозиториям — проверка не участвует в оценке.";
            case "no_actor_data" -> "Нет данных о возрасте аккаунтов — проверка не участвует в оценке.";
            case "not_tracked" -> "Репозиторий только поставлен на наблюдение: данных внутри дня пока нет.";
            case "insufficient_tracking" -> "Наблюдаем репозиторий слишком недолго - проверка пока не участвует в оценке.";
            case "no_global_profile" -> "Нет общего профиля GitHub по часам - проверка не участвует в оценке.";
            case "no_baseline" -> "Репозиторий слишком новый, чтобы сравнить с обычным темпом.";
            case "not_young" -> "Репозиторий не новый - проверка не участвует в оценке.";
            case "no_github_data" -> "Нет данных GitHub - проверка не участвует в оценке.";
            default -> null;
        };
    }

    private static int num(Map<String, Object> d, String key) {
        return d.get(key) instanceof Number n ? n.intValue() : 0;
    }

    private static double dbl(Map<String, Object> d, String key) {
        return d.get(key) instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static String devActivitySummary(Map<String, Object> d) {
        String activity = "%d issues, %d PR, %d коммитов".formatted(
                num(d, "issues"), num(d, "pull_requests"), num(d, "commits"));
        if (Boolean.TRUE.equals(d.get("steady_inflow"))) {
            return "Звёзды идут в обычном для репозитория темпе (%s в день), активность: %s."
                    .formatted(decimal(dbl(d, "baseline_per_day")), activity);
        }
        int stars = num(d, "unexplained_stars");
        return "На %d %s сверх обычного темпа за %d дн. пришлось %s.".formatted(
                stars, stars(stars), num(d, "days"), activity);
    }

    private static String decimal(double value) {
        return value >= 10 ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String hours(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(hour -> "%02d:00".formatted(((Number) hour).intValue()))
                    .collect(java.util.stream.Collectors.joining(" и "));
        }
        return "-";
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    static String stars(int n) {
        return plural(n, "звезда", "звезды", "звёзд");
    }

    private static String actions(int n) {
        return plural(n, "действие", "действия", "действий");
    }

    static String plural(int n, String one, String few, String many) {
        int mod100 = Math.abs(n) % 100;
        int mod10 = mod100 % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return many;
        }
        if (mod10 == 1) {
            return one;
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return few;
        }
        return many;
    }
}
