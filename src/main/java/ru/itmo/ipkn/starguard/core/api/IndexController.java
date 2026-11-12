package ru.itmo.ipkn.starguard.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.ipkn.starguard.core.api.dto.TopReposResponse;
import ru.itmo.ipkn.starguard.core.api.dto.WindowDto;
import ru.itmo.ipkn.starguard.core.github.RepoExistenceService;
import ru.itmo.ipkn.starguard.core.index.IndexNotReadyException;
import ru.itmo.ipkn.starguard.core.index.IndexedRepo;
import ru.itmo.ipkn.starguard.core.index.RepoIndex;
import ru.itmo.ipkn.starguard.core.index.RepoIndexSnapshot;
import ru.itmo.ipkn.starguard.core.scoring.RepoScore;
import ru.itmo.ipkn.starguard.core.scoring.ScoreLevel;
import ru.itmo.ipkn.starguard.core.service.RepoReportService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Validated
@RestController
@RequestMapping(path = "/api/v1/index", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "index", description = "Репозитории, которые можно запрашивать у сервиса")
public class IndexController {

    /** Сколько строк топа максимум просматриваем за запрос, отсеивая удалённые. */
    private static final int MAX_SCANNED = 1000;
    private static final int CHUNK = 50;

    private final ObjectProvider<RepoIndex> repoIndexProvider;
    private final ObjectProvider<RepoExistenceService> existenceProvider;
    private final ObjectProvider<RepoReportService> reportProvider;

    public IndexController(
            ObjectProvider<RepoIndex> repoIndexProvider,
            ObjectProvider<RepoExistenceService> existenceProvider,
            ObjectProvider<RepoReportService> reportProvider) {
        this.repoIndexProvider = repoIndexProvider;
        this.existenceProvider = existenceProvider;
        this.reportProvider = reportProvider;
    }

    @GetMapping("/repos")
    @Operation(
            summary = "Топ репозиториев индекса",
            description = """
                    Возвращает репозитории, для которых сервис может отдать статистику
                    без полного сканирования GH Archive. Остальные запросы получают
                    404 REPO_NOT_INDEXED. Пока индекс строится — 503 INDEX_NOT_READY.

                    По умолчанию удалённые на GitHub репозитории (часто это и есть накрутка,
                    которую GitHub уже вычистил) пропускаются: existing_only=false вернёт всё.
                    with_score=true добавит оценку, max_level=low оставит только репозитории
                    без признаков накрутки.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список доступных репозиториев"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры"),
            @ApiResponse(responseCode = "503", description = "Индекс отключён или ещё строится")
    })
    public TopReposResponse topRepos(
            @Parameter(description = "Сколько строк вернуть", example = "20")
            @RequestParam(name = "limit", defaultValue = "1000") @Min(1) @Max(5000) int limit,
            @Parameter(description = "Пропускать удалённые/скрытые на GitHub репозитории")
            @RequestParam(name = "existing_only", defaultValue = "true") boolean existingOnly,
            @Parameter(description = "Добавить оценку Star Guard к каждой строке")
            @RequestParam(name = "with_score", defaultValue = "false") boolean withScore,
            @Parameter(description = "Оставить строки с уровнем не выше указанного (включает with_score)")
            @RequestParam(name = "max_level", required = false)
            @Pattern(regexp = "^(low|medium|high|critical)$") String maxLevel) {
        RepoIndex repoIndex = repoIndexProvider.getIfAvailable();
        if (repoIndex == null) {
            throw new IndexNotReadyException(
                    "Repository index is disabled (fixture mode or app.index.enabled=false)");
        }
        RepoIndexSnapshot snapshot = repoIndex.snapshot()
                .orElseThrow(() -> new IndexNotReadyException(
                        "Repository index is still warming up; retry later"));

        RepoExistenceService existence = existenceProvider.getIfAvailable();
        boolean checkExistence = existingOnly && existence != null && existence.enabled();
        RepoReportService reports = reportProvider.getIfAvailable();
        ScoreLevel levelCap = maxLevel == null ? null : ScoreLevel.valueOf(maxLevel.toUpperCase(Locale.ROOT));
        boolean score = (withScore || levelCap != null) && reports != null;

        List<IndexedRepo> ranked = snapshot.top(existingOnly || levelCap != null
                ? Math.min(snapshot.repos().size(), Math.max(limit, MAX_SCANNED))
                : limit);
        List<TopReposResponse.TopRepoDto> repos = new ArrayList<>(Math.min(limit, ranked.size()));
        int skippedDeleted = 0;
        int skippedByLevel = 0;
        for (int from = 0; from < ranked.size() && repos.size() < limit; from += CHUNK) {
            List<IndexedRepo> chunk = ranked.subList(from, Math.min(ranked.size(), from + CHUNK));
            Map<String, Boolean> exists = checkExistence
                    ? existence.check(chunk.stream().map(r -> r.repo().fullName()).toList())
                    : Map.of();
            for (int i = 0; i < chunk.size() && repos.size() < limit; i++) {
                IndexedRepo indexed = chunk.get(i);
                Boolean present = exists.get(indexed.repo().fullName());
                if (checkExistence && Boolean.FALSE.equals(present)) {
                    skippedDeleted++;
                    continue;
                }
                Integer value = null;
                String level = null;
                String verdict = null;
                if (score) {
                    try {
                        RepoScore repoScore = reports.indexReport(indexed.repo(), null, null).score();
                        if (levelCap != null && repoScore.level().ordinal() > levelCap.ordinal()) {
                            skippedByLevel++;
                            continue;
                        }
                        value = repoScore.score();
                        level = repoScore.level().name().toLowerCase(Locale.ROOT);
                        verdict = ExplainTexts.verdict(repoScore);
                    } catch (RuntimeException e) {
                        if (levelCap != null) {
                            continue;
                        }
                    }
                }
                repos.add(new TopReposResponse.TopRepoDto(
                        from + i + 1,
                        indexed.repo().owner(),
                        indexed.repo().name(),
                        indexed.repo().fullName(),
                        RepoIndexSnapshot.totalStars(indexed),
                        present,
                        value,
                        level,
                        verdict));
            }
        }
        return new TopReposResponse(
                new WindowDto(snapshot.window().from(), snapshot.window().to(), snapshot.window().hours()),
                snapshot.builtAt(),
                snapshot.repos().size(),
                snapshot.hoursLoaded(),
                snapshot.missingHours(),
                snapshot.parseErrors(),
                skippedDeleted,
                skippedByLevel,
                repos);
    }
}
