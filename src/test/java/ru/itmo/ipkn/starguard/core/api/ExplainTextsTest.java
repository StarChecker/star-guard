package ru.itmo.ipkn.starguard.core.api;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoStats;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.CheckContribution;
import ru.itmo.ipkn.starguard.core.scoring.CheckIds;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoreLevel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExplainTextsTest {

    private static final TimeWindow WINDOW = new TimeWindow(
            Instant.parse("2026-11-10T00:00:00Z"), Instant.parse("2026-11-10T03:00:00Z"), 3);
    private static final RepoStats STATS = new RepoStats(new RepoRef("demo", "repo"), WINDOW,
            EventCounts.empty(), 0, 0, 21, List.of());

    @Test
    void pluralizesRussianNouns() {
        assertThat(ExplainTexts.stars(1)).isEqualTo("звезда");
        assertThat(ExplainTexts.stars(3)).isEqualTo("звезды");
        assertThat(ExplainTexts.stars(11)).isEqualTo("звёзд");
        assertThat(ExplainTexts.stars(21)).isEqualTo("звезда");
        assertThat(ExplainTexts.stars(25)).isEqualTo("звёзд");
    }

    @Test
    void summarizesEachRealCheck() {
        assertThat(ExplainTexts.summary(check(CheckIds.BURST, Map.of("mocked", false)), STATS))
                .isEqualTo("Пик — 21 звезда за 10 минут.");
        assertThat(ExplainTexts.summary(check(CheckIds.ENGAGEMENT,
                Map.of("mocked", false, "stars", 120, "activity", 2)), STATS))
                .isEqualTo("На 120 звёзд — 2 действия (форки, issues, PR, пуши).");
        assertThat(ExplainTexts.summary(check(CheckIds.FRESH_ACCOUNTS, Map.of("mocked", false,
                "fresh_actors", 22, "star_actors", 25, "new_account_days", 30, "fresh_share", 0.88)), STATS))
                .isEqualTo("22 из 25 звёзд — от аккаунтов моложе 30 дн. или без активности (88%).");
        assertThat(ExplainTexts.summary(check(CheckIds.ACTOR_OVERLAP, Map.of("mocked", false,
                "star_actors", 32, "multi_repo_actors", 30, "multi_repo_threshold", 3,
                "top_overlaps", List.of(Map.of("repo", "demo/farm-1", "jaccard", 0.94)))), STATS))
                .isEqualTo("30 из 32 отметивших репозиторий поставили звёзды ещё ≥2 репозиториям за окно. "
                        + "Сильнее всего совпадает аудитория demo/farm-1 (94%).");
    }

    @Test
    void explainsMockedAndInactiveChecks() {
        assertThat(ExplainTexts.summary(check(CheckIds.CIRCADIAN, Map.of("mocked", true)), STATS))
                .startsWith("Демо-значение");
        assertThat(ExplainTexts.summary(check(CheckIds.ENGAGEMENT,
                Map.of("mocked", false, "reason", "too_few_stars")), STATS))
                .startsWith("Слишком мало звёзд");
        assertThat(ExplainTexts.summary(check(CheckIds.ACTOR_OVERLAP,
                Map.of("mocked", false, "reason", "no_cross_repo_index")), STATS))
                .startsWith("Нет индекса звёзд");
    }

    @Test
    void verdictNamesMainSignal() {
        RepoScore high = new RepoScore(72, ScoreLevel.HIGH, 0.8, false, List.of(
                new CheckContribution(CheckIds.ACTOR_OVERLAP, 1.0, 0.8, 0.22, 0.4, true, null, Map.of()),
                new CheckContribution(CheckIds.ENGAGEMENT, 0.5, 0.9, 0.18, 0.1, true, null, Map.of())),
                false, List.of(), Instant.EPOCH);
        assertThat(ExplainTexts.verdict(high))
                .isEqualTo("Заметные признаки накрутки: главный сигнал — «пересечение аккаунтов».");

        RepoScore low = new RepoScore(5, ScoreLevel.LOW, 0.8, false, List.of(), false, List.of(), Instant.EPOCH);
        assertThat(ExplainTexts.verdict(low)).isEqualTo("Признаков накрутки не найдено.");

        RepoScore unknown = new RepoScore(0, ScoreLevel.LOW, 0.0, true, List.of(), false, List.of(), Instant.EPOCH);
        assertThat(ExplainTexts.verdict(unknown)).startsWith("Недостаточно данных");
    }

    private static CheckContribution check(String id, Map<String, Object> details) {
        return new CheckContribution(id, 0.5, 0.8, 0.2, 0.1, true, null, details);
    }
}
