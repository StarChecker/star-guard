package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Звёзды по времени с разметкой подозрительных — данные для главного графика расширения.
 *
 * @param bucketMinutes      ширина точки графика в минутах
 * @param estimated          подозрительные в точках оценены пропорционально внутри часа
 *                           (в режиме индекса акторы известны только по часам)
 * @param points             точки графика по порядку
 * @param suspiciousStars    сколько звёзд окна помечено подозрительными
 * @param suspiciousByCheck  id проверки → число помеченных ею звёзд (звезда может попасть в несколько)
 */
public record StarTimeline(
        int bucketMinutes,
        boolean estimated,
        List<Point> points,
        int suspiciousStars,
        Map<String, Integer> suspiciousByCheck,
        List<SpikeInterval> spikeIntervals
) {

    public StarTimeline(int bucketMinutes, boolean estimated, List<Point> points,
                        int suspiciousStars, Map<String, Integer> suspiciousByCheck) {
        this(bucketMinutes, estimated, points, suspiciousStars, suspiciousByCheck, List.of());
    }

    public StarTimeline {
        spikeIntervals = List.copyOf(spikeIntervals);
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        suspiciousByCheck = Map.copyOf(Objects.requireNonNull(suspiciousByCheck, "suspiciousByCheck"));
    }

    public static StarTimeline empty(int bucketMinutes) {
        return new StarTimeline(bucketMinutes, false, List.of(), 0, Map.of());
    }

    /** Самое насыщенное окно C1 [from, to); peak — максимум звёзд за минуту. */
    public record SpikeInterval(Instant from, Instant to, long stars, double ratio, int peak, boolean suspicious) {
    }

    /** Одна точка: начало интервала, все звёзды и подозрительные из них. */
    public record Point(Instant at, int stars, int suspicious) {

        public Point {
            Objects.requireNonNull(at, "at");
        }
    }
}
