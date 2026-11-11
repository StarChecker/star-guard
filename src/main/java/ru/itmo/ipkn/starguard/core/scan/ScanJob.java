package ru.itmo.ipkn.starguard.core.scan;

import ru.itmo.ipkn.starguard.core.domain.RepoRef;
import ru.itmo.ipkn.starguard.core.service.RepoReport;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Фоновый скан GH Archive для репозитория вне индекса. Изменяемое состояние — только через сервис. */
public final class ScanJob {

    public enum Status { QUEUED, RUNNING, DONE, FAILED }

    private final String id;
    private final RepoRef repo;
    private final int hoursTotal;
    private final Instant createdAt;
    private final AtomicInteger hoursDone = new AtomicInteger();
    private volatile Status status = Status.QUEUED;
    private volatile RepoReport report;
    private volatile String errorCode;
    private volatile String errorMessage;

    ScanJob(String id, RepoRef repo, int hoursTotal, Instant createdAt) {
        this.id = id;
        this.repo = repo;
        this.hoursTotal = hoursTotal;
        this.createdAt = createdAt;
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

    void progress(int done) {
        hoursDone.accumulateAndGet(done, Math::max);
    }

    void done(RepoReport result) {
        report = result;
        hoursDone.set(hoursTotal);
        status = Status.DONE;
    }

    void failed(String code, String message) {
        errorCode = code;
        errorMessage = message;
        status = Status.FAILED;
    }
}
