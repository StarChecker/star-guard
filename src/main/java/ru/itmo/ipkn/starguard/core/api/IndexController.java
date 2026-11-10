package ru.itmo.ipkn.starguard.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.ipkn.starguard.core.api.dto.TopReposResponse;
import ru.itmo.ipkn.starguard.core.api.dto.WindowDto;
import ru.itmo.ipkn.starguard.core.index.IndexNotReadyException;
import ru.itmo.ipkn.starguard.core.index.IndexedRepo;
import ru.itmo.ipkn.starguard.core.index.RepoIndex;
import ru.itmo.ipkn.starguard.core.index.RepoIndexSnapshot;

import java.util.ArrayList;
import java.util.List;

@Validated
@RestController
@RequestMapping(path = "/api/v1/index", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "index", description = "Репозитории, которые можно запрашивать у сервиса")
public class IndexController {

    private final ObjectProvider<RepoIndex> repoIndexProvider;

    public IndexController(ObjectProvider<RepoIndex> repoIndexProvider) {
        this.repoIndexProvider = repoIndexProvider;
    }

    @GetMapping("/repos")
    @Operation(
            summary = "Топ репозиториев индекса",
            description = """
                    Возвращает репозитории, для которых сервис может отдать статистику
                    без полного сканирования GH Archive. Остальные запросы получают
                    404 REPO_NOT_INDEXED. Пока индекс строится — 503 INDEX_NOT_READY.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список доступных репозиториев"),
            @ApiResponse(responseCode = "400", description = "Некорректный limit"),
            @ApiResponse(responseCode = "503", description = "Индекс отключён или ещё строится")
    })
    public TopReposResponse topRepos(
            @Parameter(description = "Сколько строк вернуть", example = "1000")
            @RequestParam(name = "limit", defaultValue = "1000") @Min(1) @Max(5000) int limit) {
        RepoIndex repoIndex = repoIndexProvider.getIfAvailable();
        if (repoIndex == null) {
            throw new IndexNotReadyException(
                    "Repository index is disabled (fixture mode or app.index.enabled=false)");
        }
        RepoIndexSnapshot snapshot = repoIndex.snapshot()
                .orElseThrow(() -> new IndexNotReadyException(
                        "Repository index is still warming up; retry later"));

        List<TopReposResponse.TopRepoDto> repos = new ArrayList<>(limit);
        int rank = 1;
        for (IndexedRepo indexed : snapshot.top(limit)) {
            repos.add(new TopReposResponse.TopRepoDto(
                    rank++,
                    indexed.repo().owner(),
                    indexed.repo().name(),
                    indexed.repo().fullName(),
                    RepoIndexSnapshot.totalStars(indexed)));
        }
        return new TopReposResponse(
                new WindowDto(snapshot.window().from(), snapshot.window().to(), snapshot.window().hours()),
                snapshot.builtAt(),
                snapshot.repos().size(),
                snapshot.hoursLoaded(),
                snapshot.missingHours(),
                snapshot.parseErrors(),
                repos);
    }
}
