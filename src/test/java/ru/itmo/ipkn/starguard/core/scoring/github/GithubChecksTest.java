package ru.itmo.ipkn.starguard.core.scoring.github;

import org.junit.jupiter.api.Test;
import ru.itmo.ipkn.starguard.core.config.BurstProperties;
import ru.itmo.ipkn.starguard.core.config.GithubChecksProperties;
import ru.itmo.ipkn.starguard.core.domain.DataAvailability;
import ru.itmo.ipkn.starguard.core.domain.EventCounts;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;
import ru.itmo.ipkn.starguard.core.domain.TimeWindow;
import ru.itmo.ipkn.starguard.core.scoring.CheckMode;
import ru.itmo.ipkn.starguard.core.scoring.CheckResult;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.itmo.ipkn.starguard.core.scoring.github.GithubSnapshots.TODAY;
import static ru.itmo.ipkn.starguard.core.scoring.github.GithubSnapshots.intraday;
import static ru.itmo.ipkn.starguard.core.scoring.github.GithubSnapshots.repo;
import static ru.itmo.ipkn.starguard.core.scoring.github.GithubSnapshots.steady;
import static ru.itmo.ipkn.starguard.core.scoring.github.GithubSnapshots.uniformHours;

class GithubChecksTest {

    private static final GithubChecksProperties PROPERTIES = GithubChecksProperties.defaults();
    private static final BurstProperties BURST = new BurstProperties(CheckMode.REAL, true, 3.0, 12.0, 10, 60, 0.3);

    @Test
    void dailyBurstFindsSpikeAgainstOwnBaseline() {
        int[] daily = steady(120, 40);
        daily[110] = 1240;
        CheckResult result = new DailyBurstCheck(PROPERTIES.dailyBurst())
                .evaluate(repo().daily(TODAY.minusDays(119), daily).build());

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.details())
                .containsEntry("best_day", TODAY.minusDays(9).toString())
                .containsEntry("best_stars", 1240)
                .containsEntry("burst_day_count", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bursts = (List<Map<String, Object>>) result.details().get("burst_days");
        assertThat(bursts.getFirst()).containsEntry("excess", 1200);
    }

    @Test
    void dailyBurstStaysQuietOnSteadyGrowth() {
        CheckResult result = new DailyBurstCheck(PROPERTIES.dailyBurst()).evaluate(repo().build());

        assertThat(result.score()).isZero();
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.details()).containsEntry("burst_day_count", 0);
    }

    @Test
    void dailyBurstIgnoresSmallAbsoluteSpikes() {
        int[] daily = steady(120, 2);
        daily[115] = 60;

        CheckResult result = new DailyBurstCheck(PROPERTIES.dailyBurst())
                .evaluate(repo().daily(TODAY.minusDays(119), daily).build());

        assertThat(result.score()).isLessThan(0.6);
        assertThat(result.details()).containsEntry("burst_day_count", 0);
    }

    @Test
    void dailyBurstNeedsBaselineDays() {
        CheckResult result = new DailyBurstCheck(PROPERTIES.dailyBurst())
                .evaluate(repo().created("2026-11-09T00:00:00Z")
                        .daily(LocalDate.parse("2026-11-09"), 10, 900, 1500, 40).build());

        assertThat(result.confidence()).isZero();
        assertThat(result.details()).containsEntry("reason", "no_baseline");
    }

    @Test
    void devActivityFlagsStarsWithoutDevelopment() {
        CheckResult result = new DevActivityCheck(PROPERTIES.devActivity())
                .evaluate(repo().created("2026-11-03T14:04:27Z")
                        .daily(LocalDate.parse("2026-11-03"), 273, 680, 328, 536, 682, 1617, 2859, 1161, 202, 70)
                        .activity(3102, 38, 26, 1, 1).build());

        assertThat(result.score()).isGreaterThanOrEqualTo(0.8);
        assertThat(result.details()).containsEntry("commits", 1);
    }

    @Test
    void devActivityTreatsSteadyInflowOfMatureRepositoryAsExplained() {
        CheckResult result = new DevActivityCheck(PROPERTIES.devActivity())
                .evaluate(repo().daily(TODAY.minusDays(119), steady(120, 600)).activity(100, 0, 0, 0, 0).build());

        assertThat(result.score()).isZero();
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.details()).containsEntry("steady_inflow", true);
    }

    @Test
    void devActivityFlagsBurstWithoutActivityInMatureRepository() {
        int[] daily = steady(120, 60);
        for (int i = 105; i < 113; i++) {
            daily[i] = 1800;
        }
        CheckResult result = new DevActivityCheck(PROPERTIES.devActivity())
                .evaluate(repo().daily(TODAY.minusDays(119), daily).activity(900, 10, 20, 1, 2).build());

        assertThat(result.score()).isGreaterThanOrEqualTo(0.9);
        assertThat(result.details()).containsEntry("unexplained_stars", 13920);
    }

    @Test
    void devActivityIsCalmWhenBurstComesWithDevelopment() {
        int[] daily = steady(120, 60);
        for (int i = 105; i < 113; i++) {
            daily[i] = 1800;
        }
        CheckResult result = new DevActivityCheck(PROPERTIES.devActivity())
                .evaluate(repo().daily(TODAY.minusDays(119), daily).activity(900, 600, 500, 800, 90).build());

        assertThat(result.score()).isZero();
    }

    @Test
    void forkRatioFlagsForkFarming() {
        CheckResult result = new ForkRatioCheck(PROPERTIES.forkRatio())
                .evaluate(repo().created("2026-11-03T14:04:27Z")
                        .daily(LocalDate.parse("2026-11-03"), 273, 680, 328, 536, 682, 1617, 2859, 1161, 202, 70)
                        .activity(6000, 38, 26, 1, 1).build());

        assertThat(result.score()).isGreaterThanOrEqualTo(0.8);
    }

    @Test
    void forkRatioIsCalmForTypicalRatio() {
        CheckResult result = new ForkRatioCheck(PROPERTIES.forkRatio())
                .evaluate(repo().created("2026-10-20T00:00:00Z")
                        .daily(LocalDate.parse("2026-10-20"), steady(24, 100)).activity(240, 50, 40, 90, 12).build());

        assertThat(result.score()).isZero();
        assertThat(result.details()).containsEntry("forks_per_star", 0.1);
    }

    @Test
    void forkRatioSkipsMatureRepositories() {
        CheckResult result = new ForkRatioCheck(PROPERTIES.forkRatio())
                .evaluate(repo().activity(1400, 10, 10, 10, 5).build());

        assertThat(result.confidence()).isZero();
        assertThat(result.details()).containsEntry("reason", "not_young");
    }

    @Test
    void intradayBurstUsesTrackerSnapshots() {
        int[] stars = new int[290];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = 1000 + i;
        }
        for (int i = 200; i < stars.length; i++) {
            stars[i] += 400;
        }
        CheckResult result = new IntradayBurstCheck(PROPERTIES.intradayBurst(), BURST)
                .evaluate(repo().intraday(intraday(stars, new int[0], new double[0])).build());

        assertThat(result.score()).isGreaterThanOrEqualTo(0.9);
        assertThat(result.confidence()).isGreaterThan(0.85);
        assertThat(result.details()).containsKey("max_burst_10m");
    }

    @Test
    void intradayBurstNeedsEnoughTracking() {
        int[] stars = new int[290];
        Arrays.fill(stars, -1);
        stars[288] = 100;
        stars[289] = 101;

        CheckResult result = new IntradayBurstCheck(PROPERTIES.intradayBurst(), BURST)
                .evaluate(repo().intraday(intraday(stars, new int[0], new double[0])).build());

        assertThat(result.confidence()).isZero();
        assertThat(result.details()).containsEntry("reason", "insufficient_tracking");
    }

    @Test
    void intradayChecksAreInactiveWhenRepositoryIsNotTracked() {
        RepoSnapshot snapshot = repo().build();

        assertThat(new IntradayBurstCheck(PROPERTIES.intradayBurst(), BURST).evaluate(snapshot).details())
                .containsEntry("reason", "not_tracked");
        assertThat(new HourlyProfileCheck(PROPERTIES.hourlyProfile()).evaluate(snapshot).details())
                .containsEntry("reason", "not_tracked");
        assertThat(new StarDropsCheck(PROPERTIES.starDrops()).evaluate(snapshot).details())
                .containsEntry("reason", "not_tracked");
    }

    @Test
    void hourlyProfileFlagsStarsConcentratedInTwoHours() {
        int[] hourly = new int[169];
        int total = 0;
        for (int i = 0; i < hourly.length; i++) {
            int hourOfDay = (10 + i) % 24;
            if (i > 0 && (hourOfDay == 3 || hourOfDay == 4)) {
                total += 20;
            }
            hourly[i] = total;
        }
        CheckResult result = new HourlyProfileCheck(PROPERTIES.hourlyProfile())
                .evaluate(repo().intraday(intraday(new int[0], hourly, uniformHours())).build());

        assertThat(result.score()).isGreaterThanOrEqualTo(0.9);
        assertThat(result.details()).containsEntry("peak_hours_utc", List.of(3, 4));
    }

    @Test
    void hourlyProfileIsCalmWhenStarsFollowGithubRhythm() {
        int[] hourly = new int[169];
        for (int i = 0; i < hourly.length; i++) {
            hourly[i] = i * 5;
        }
        CheckResult result = new HourlyProfileCheck(PROPERTIES.hourlyProfile())
                .evaluate(repo().intraday(intraday(new int[0], hourly, uniformHours())).build());

        assertThat(result.score()).isZero();
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void starDropsFlagsMassUnstarring() {
        int[] hourly = new int[169];
        for (int i = 0; i < hourly.length; i++) {
            hourly[i] = 5000 + i * 3 - (i >= 100 ? 600 : 0);
        }
        CheckResult result = new StarDropsCheck(PROPERTIES.starDrops())
                .evaluate(repo().intraday(intraday(new int[0], hourly, uniformHours())).build());

        assertThat(result.score()).isEqualTo(1.0);
        assertThat(result.details()).containsEntry("biggest_drop", 597);
    }

    @Test
    void starDropsIgnoresUsualUnstars() {
        int[] hourly = new int[169];
        for (int i = 0; i < hourly.length; i++) {
            hourly[i] = 5000 + i * 10 - (i % 24 == 0 ? 15 : 0);
        }
        CheckResult result = new StarDropsCheck(PROPERTIES.starDrops())
                .evaluate(repo().intraday(intraday(new int[0], hourly, uniformHours())).build());

        assertThat(result.score()).isZero();
        assertThat(result.details()).containsEntry("dropped", 35L);
    }

    @Test
    void checksAreInactiveWithoutGithubData() {
        RepoSnapshot archive = new RepoSnapshot(new RepoRef("acme", "tool"),
                new TimeWindow(GithubSnapshots.WINDOW_FROM, GithubSnapshots.WINDOW_TO, 707),
                GithubSnapshots.NOW, EventCounts.empty(), 0, 0, null, DataAvailability.pocDefaults());

        assertThat(new DailyBurstCheck(PROPERTIES.dailyBurst()).evaluate(archive).details())
                .containsEntry("reason", "no_github_data");
        assertThat(new ForkRatioCheck(PROPERTIES.forkRatio()).evaluate(archive).confidence()).isZero();
    }
}
