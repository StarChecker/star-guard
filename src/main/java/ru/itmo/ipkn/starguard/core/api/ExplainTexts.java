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

    private static final Map<String, String> TITLES = Map.of(
            CheckIds.BURST, "Всплески звёзд",
            CheckIds.FRESH_ACCOUNTS, "Свежие и пустые аккаунты",
            CheckIds.ENGAGEMENT, "Звёзды без активности",
            CheckIds.ACTOR_OVERLAP, "Пересечение аккаунтов",
            CheckIds.CIRCADIAN, "Суточный ритм");

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
            case CheckIds.BURST -> "Пик — %d %s за 10 минут.".formatted(
                    stats.peakStars10m(), stars(stats.peakStars10m()));
            case CheckIds.FRESH_ACCOUNTS -> "%d из %d %s — от аккаунтов моложе %d дн. или без активности (%s)."
                    .formatted(num(d, "fresh_actors"), num(d, "star_actors"), stars(num(d, "star_actors")),
                            num(d, "new_account_days"), percent(dbl(d, "fresh_share")));
            case CheckIds.ENGAGEMENT -> "На %d %s — %d %s (форки, issues, PR, пуши)."
                    .formatted(num(d, "stars"), stars(num(d, "stars")),
                            num(d, "activity"), actions(num(d, "activity")));
            case CheckIds.ACTOR_OVERLAP -> overlapSummary(d);
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
            case "too_few_stars", "too_few_star_actors", "no_star_actors" ->
                    "Слишком мало звёзд за окно — проверка не участвует в оценке.";
            case "no_cross_repo_index" -> "Нет индекса звёзд по другим репозиториям — проверка не участвует в оценке.";
            case "no_actor_data" -> "Нет данных о возрасте аккаунтов — проверка не участвует в оценке.";
            default -> null;
        };
    }

    private static int num(Map<String, Object> d, String key) {
        return d.get(key) instanceof Number n ? n.intValue() : 0;
    }

    private static double dbl(Map<String, Object> d, String key) {
        return d.get(key) instanceof Number n ? n.doubleValue() : 0.0;
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
