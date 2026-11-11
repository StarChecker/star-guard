package ru.itmo.ipkn.starguard.core.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itmo.ipkn.starguard.core.api.dto.PreviewDto;
import ru.itmo.ipkn.starguard.core.api.dto.RepoDto;
import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.github.GithubPreviewService;
import ru.itmo.ipkn.starguard.core.github.RepoPreview;

/** F5: preview холодного репозитория по GitHub REST — базовая статистика без score. */
@Validated
@RestController
@RequestMapping(path = "/api/v1/preview", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "preview", description = "Базовая статистика GitHub для репозиториев без событий в GH Archive")
public class PreviewController {

    static final String NOTE = "Нет данных GH Archive: базовая статистика GitHub, без оценки.";
    private static final String NAME_PATTERN = "^[A-Za-z0-9_.-]{1,100}$";

    private final GithubPreviewService previewService;

    public PreviewController(GithubPreviewService previewService) {
        this.previewService = previewService;
    }

    @GetMapping("/{owner}/{name}")
    @Operation(
            summary = "Preview репозитория по GitHub",
            description = """
                    Звёзды, форки, открытые issues, наблюдатели и контрибьюторы из GitHub REST API.
                    Работает для любого существующего репозитория, токен необязателен
                    (без app.github.token лимит GitHub — 60 запросов в час, ответы кэшируются).
                    Это не оценка: score считается отдельно — через индекс, скан или историю GitHub.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Базовая статистика, partial = true"),
            @ApiResponse(responseCode = "400", description = "Некорректное имя репозитория"),
            @ApiResponse(responseCode = "404", description = "REPO_NOT_FOUND_ON_GITHUB — удалён или приватный"),
            @ApiResponse(responseCode = "503",
                    description = "GITHUB_RATE_LIMITED, GITHUB_UNAVAILABLE или GITHUB_PREVIEW_DISABLED")
    })
    public PreviewDto preview(
            @PathVariable @Pattern(regexp = NAME_PATTERN) String owner,
            @PathVariable @Pattern(regexp = NAME_PATTERN) String name) {
        return toDto(previewService.preview(new RepoRef(owner, name)));
    }

    static PreviewDto toDto(RepoPreview preview) {
        RepoRef repo = preview.repo();
        return new PreviewDto(
                new RepoDto(repo.owner(), repo.name(), repo.fullName()),
                "github",
                true,
                NOTE,
                preview.description(),
                preview.htmlUrl(),
                preview.stars(),
                preview.forks(),
                preview.openIssues(),
                preview.watchers(),
                preview.contributors(),
                preview.archived(),
                preview.fork(),
                preview.createdAt(),
                preview.pushedAt(),
                preview.fetchedAt(),
                preview.cached());
    }
}
