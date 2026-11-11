package ru.itmo.ipkn.starguard.core.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.itmo.ipkn.starguard.core.archive.ArchiveUnavailableException;
import ru.itmo.ipkn.starguard.core.archive.InvalidWindowException;
import ru.itmo.ipkn.starguard.core.github.GithubUnavailableException;
import ru.itmo.ipkn.starguard.core.github.RepoGoneException;
import ru.itmo.ipkn.starguard.core.index.IndexNotReadyException;
import ru.itmo.ipkn.starguard.core.index.PinLimitExceededException;
import ru.itmo.ipkn.starguard.core.index.RepoNotIndexedException;
import ru.itmo.ipkn.starguard.core.scan.ScanNotFoundException;
import ru.itmo.ipkn.starguard.core.scoring.InvalidMockModeException;
import ru.itmo.ipkn.starguard.core.service.RepoNotFoundException;

import java.net.URI;

/** Ошибки API в формате RFC 9457 ProblemDetail с полем code. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RepoNotFoundException.class)
    public ProblemDetail handleNotFound(RepoNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "REPO_NOT_FOUND_IN_WINDOW", e.getMessage(), request);
    }

    @ExceptionHandler(RepoGoneException.class)
    public ProblemDetail handleGone(RepoGoneException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "REPO_NOT_FOUND_ON_GITHUB", e.getMessage(), request);
    }

    @ExceptionHandler(GithubUnavailableException.class)
    public ProblemDetail handleGithub(GithubUnavailableException e, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, e.code(), e.getMessage(), request);
    }

    @ExceptionHandler(ScanNotFoundException.class)
    public ProblemDetail handleScanNotFound(ScanNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "SCAN_NOT_FOUND", e.getMessage(), request);
    }

    @ExceptionHandler(RepoNotIndexedException.class)
    public ProblemDetail handleNotIndexed(
            RepoNotIndexedException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "REPO_NOT_INDEXED", e.getMessage(), request);
    }

    @ExceptionHandler(IndexNotReadyException.class)
    public ProblemDetail handleIndexNotReady(
            IndexNotReadyException e, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "INDEX_NOT_READY", e.getMessage(), request);
    }

    @ExceptionHandler(PinLimitExceededException.class)
    public ProblemDetail handlePinLimit(
            PinLimitExceededException e, HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "PIN_LIMIT_REACHED", e.getMessage(), request);
    }

    @ExceptionHandler(ArchiveUnavailableException.class)
    public ProblemDetail handleArchiveUnavailable(
            ArchiveUnavailableException e, HttpServletRequest request) {
        log.warn("GH Archive unavailable: {}", e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "ARCHIVE_UNAVAILABLE", e.getMessage(), request);
    }

    @ExceptionHandler({
            InvalidWindowException.class,
            InvalidMockModeException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ProblemDetail handleBadRequest(Exception e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error while handling {}", request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected server error", request);
    }

    private static ProblemDetail problem(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
