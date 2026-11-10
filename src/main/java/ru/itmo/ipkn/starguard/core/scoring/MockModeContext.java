package ru.itmo.ipkn.starguard.core.scoring;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * PoC-контекст выбранного профиля моков на время одного запроса.
 * Позволяет переключать профиль query-параметром, не меняя контракт {@link RepoCheck}.
 */
@Component
public class MockModeContext {

    private final ThreadLocal<MockMode> current = new ThreadLocal<>();

    public Optional<MockMode> current() {
        return Optional.ofNullable(current.get());
    }

    public <T> T callWith(MockMode mode, Supplier<T> action) {
        MockMode previous = current.get();
        set(mode);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
        }
    }

    private void set(MockMode mode) {
        if (mode == null) {
            current.remove();
        } else {
            current.set(mode);
        }
    }
}
