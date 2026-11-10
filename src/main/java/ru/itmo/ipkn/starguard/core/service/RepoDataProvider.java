package ru.itmo.ipkn.starguard.core.service;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;

/** Источник данных для отчёта: индекс или прямой скан GH Archive. */
public interface RepoDataProvider {

    RepoData load(RepoRef repo, Integer requestedHours);
}
