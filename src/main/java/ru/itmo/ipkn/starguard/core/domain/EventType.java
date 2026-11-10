package ru.itmo.ipkn.starguard.core.domain;

/** Типы событий GH Archive, которые нужны PoC. */
public enum EventType {
    WATCH,
    FORK,
    PUSH,
    ISSUES,
    PULL_REQUEST,
    OTHER
}
