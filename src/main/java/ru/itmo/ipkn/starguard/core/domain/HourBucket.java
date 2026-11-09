package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;

/** Звёзды за один час окна (для графика в API). */
public record HourBucket(Instant hour, int stars) {
}
