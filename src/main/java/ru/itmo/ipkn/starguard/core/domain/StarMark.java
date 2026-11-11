package ru.itmo.ipkn.starguard.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Одна звезда репозитория: кто и когда. Источник для таймлайна звёзд с разметкой подозрительных.
 *
 * @param at             момент звезды; при {@code hourResolution} — начало часа
 * @param actorId        актор, поставивший звезду
 * @param hourResolution точное время неизвестно (режим индекса хранит акторов по часам)
 */
public record StarMark(Instant at, long actorId, boolean hourResolution) {

    public StarMark {
        Objects.requireNonNull(at, "at");
    }
}
