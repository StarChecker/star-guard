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

    private CheckIds() {
    }
}
