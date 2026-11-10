package ru.itmo.ipkn.starguard.core.scoring;

import ru.itmo.ipkn.starguard.core.config.ScoringProperties;
import ru.itmo.ipkn.starguard.core.domain.RepoSnapshot;

import java.util.Map;

/** Базовая мок-проверка: возвращает значения из выбранного демо-профиля. */
public abstract class AbstractMockCheck implements RepoCheck {

    private final ScoringProperties properties;
    private final MockModeContext context;

    protected AbstractMockCheck(ScoringProperties properties, MockModeContext context) {
        this.properties = properties;
        this.context = context;
    }

    @Override
    public final CheckResult evaluate(RepoSnapshot snapshot) {
        MockMode mode = context.current().orElse(properties.mockMode());
        return new CheckResult(
                id(),
                MockProfiles.score(mode, id()),
                MockProfiles.confidence(),
                Map.of("mocked", true, "profile", mode.slug()));
    }
}
