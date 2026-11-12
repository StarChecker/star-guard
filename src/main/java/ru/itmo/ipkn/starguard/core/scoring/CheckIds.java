package ru.itmo.ipkn.starguard.core.scoring;

import java.util.List;

/** Идентификаторы проверок C1–C5. */
public final class CheckIds {

    public static final String BURST = "burst";
    public static final String FRESH_ACCOUNTS = "fresh_accounts";
    public static final String ENGAGEMENT = "engagement";
    public static final String ACTOR_OVERLAP = "actor_overlap";
    public static final String CIRCADIAN = "circadian";

    /** Порядок проверок в explain и в конфигурации. */
    public static final List<String> ALL = List.of(
            BURST, FRESH_ACCOUNTS, ENGAGEMENT, ACTOR_OVERLAP, CIRCADIAN);

    public static final String DAILY_BURST = "daily_burst";
    public static final String DEV_ACTIVITY = "dev_activity";
    public static final String FORK_RATIO = "fork_ratio";
    public static final String INTRADAY_BURST = "intraday_burst";
    public static final String HOURLY_PROFILE = "hourly_profile";
    public static final String STAR_DROPS = "star_drops";

    public static final List<String> GITHUB_ALL = List.of(
            DAILY_BURST, DEV_ACTIVITY, FORK_RATIO, INTRADAY_BURST, HOURLY_PROFILE, STAR_DROPS);

    public static int order(String id) {
        int index = ALL.indexOf(id);
        if (index >= 0) {
            return index;
        }
        index = GITHUB_ALL.indexOf(id);
        return index >= 0 ? ALL.size() + index : Integer.MAX_VALUE;
    }

    private CheckIds() {
    }
}
