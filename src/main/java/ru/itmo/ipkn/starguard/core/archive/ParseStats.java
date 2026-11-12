package ru.itmo.ipkn.starguard.core.archive;

/**
 * Итог разбора одного дампа: сколько строк прочитано (включая битые и слишком длинные),
 * сколько распакованных байт они заняли и сколько строк не разобралось.
 */
public record ParseStats(long lines, long bytes, int errors) {
}
