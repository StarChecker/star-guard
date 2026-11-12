package ru.itmo.ipkn.starguard.core.github;

/** GitHub API недоступен, отказал в доступе или не настроен токен. */
public class GithubUnavailableException extends RuntimeException {

    private final String code;

    public GithubUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public GithubUnavailableException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** GITHUB_TOKEN_REQUIRED | GITHUB_UNAVAILABLE */
    public String code() {
        return code;
    }
}
