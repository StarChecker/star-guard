package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Настройки публичной выборки аккаунтов; лимит карточки задаётся отдельно в UI. */
@ConfigurationProperties(prefix = "app.api")
public record ApiProperties(ActorLogins actorLogins, Integer suspiciousActorsLimit) {

    public enum ActorLogins { SHOW, HASH }

    public ApiProperties {
        actorLogins = actorLogins == null ? ActorLogins.SHOW : actorLogins;
        suspiciousActorsLimit = suspiciousActorsLimit == null ? 50 : suspiciousActorsLimit;
        if (suspiciousActorsLimit < 1 || suspiciousActorsLimit > 500) {
            throw new IllegalArgumentException("app.api.suspicious-actors-limit must be within [1, 500]");
        }
    }
}
