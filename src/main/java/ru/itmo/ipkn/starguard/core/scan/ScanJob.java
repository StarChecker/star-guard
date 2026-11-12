package ru.itmo.ipkn.starguard.core.scan;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.service.RepoReport;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Фоновый скан GH Archive для репозитория вне индекса. Изменяемое состояние — только через сервис. */
public final class ScanJob {

    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    /** Откуда берутся данные скана: история GitHub (быстро, любой репозиторий) или дампы GH Archive. */
    public enum Source { GITHUB, ARCHIVE }

    private final String id;
    private final RepoRef repo;
    private final int hoursTotal;
    private volatile Source source;
    private final Instant createdAt;
    private final AtomicInteger stepsTotal = new AtomicInteger();
    private final AtomicInteger hoursDone = new AtomicInteger();
    private volatile Status status = Status.QUEUED;
    private volatile RepoReport report;
    private volatile String errorCode;
    private volatile String errorMessage;

    ScanJob(String id, RepoRef repo, int hoursTotal, Source source, Instant createdAt) {
        this.id = id;
        this.repo = repo;
        this.hoursTotal = hoursTotal;
        this.source = source;
        this.createdAt = createdAt;
        this.stepsTotal.set(hoursTotal);
    }

    public Source source() {
        return source;
    }

    void switchSource(Source newSource) {
        this.source = newSource;
    }

    /** Всего шагов: часов архива или страниц истории GitHub. */
    public int stepsTotal() {
        return stepsTotal.get();
    }

    public String id() {
        return id;
    }

    public RepoRef repo() {
        return repo;
    }

    public int hoursTotal() {
        return hoursTotal;
    }

    public int hoursDone() {
        return hoursDone.get();
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Status status() {
        return status;
    }

    public RepoReport report() {
        return report;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    void running() {
        status = Status.RUNNING;
    }

    void progress(int done, int total) {
        if (total > 0) {
            stepsTotal.set(total);
        }
        hoursDone.accumulateAndGet(done, Math::max);
    }

    void done(RepoReport result) {
        report = result;
        hoursDone.set(stepsTotal.get());
        status = Status.DONE;
    }

    void failed(String code, String message) {
        errorCode = code;
        errorMessage = message;
        status = Status.FAILED;
    }
}
