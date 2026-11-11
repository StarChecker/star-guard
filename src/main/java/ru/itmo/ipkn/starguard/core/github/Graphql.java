package ru.itmo.ipkn.starguard.core.github;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Безопасная навигация по ответу GraphQL, разобранному в Map/List. */
final class Graphql {

    private Graphql() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object node, String... path) {
        Object current = node;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = m.get(key);
        }
        return current instanceof Map<?, ?> ? (Map<String, Object>) current : null;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Map<String, Object> node, String key) {
        Object value = node == null ? null : node.get(key);
        return value instanceof List<?> ? (List<Object>) value : List.of();
    }

    static String string(Map<String, Object> node, String key) {
        Object value = node == null ? null : node.get(key);
        return value == null ? null : value.toString();
    }

    static boolean bool(Map<String, Object> node, String key) {
        return node != null && Boolean.TRUE.equals(node.get(key));
    }

    static long number(Map<String, Object> node, String key, long fallback) {
        Object value = node == null ? null : node.get(key);
        return value instanceof Number n ? n.longValue() : fallback;
    }

    static Instant instant(Map<String, Object> node, String key) {
        String value = string(node, key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Ошибка «не найдено» в ответе: репозиторий удалён или приватный. */
    static boolean notFound(Map<String, Object> response) {
        for (Object error : list(response, "errors")) {
            if (error instanceof Map<?, ?> e && "NOT_FOUND".equals(String.valueOf(e.get("type")))) {
                return true;
            }
        }
        return false;
    }
}
