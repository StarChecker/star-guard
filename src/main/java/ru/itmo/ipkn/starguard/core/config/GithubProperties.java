package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.github")
public record GithubProperties(
        Boolean enabled,
        String baseUrl,
        String token,
        Integer topN,
        Integer maxLookupsPerRequest,
        Duration cacheTtl,
        Long cacheMaxSize,
        Duration rateLimitPause
) {

    public GithubProperties {
        enabled = enabled != null && enabled;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.github.com" : baseUrl;
        token = token == null ? "" : token.trim();
        topN = topN == null ? 100 : topN;
        maxLookupsPerRequest = maxLookupsPerRequest == null ? 50 : maxLookupsPerRequest;
        cacheTtl = cacheTtl == null ? Duration.ofHours(24) : cacheTtl;
        cacheMaxSize = cacheMaxSize == null ? 100_000L : cacheMaxSize;
        rateLimitPause = rateLimitPause == null ? Duration.ofMinutes(1) : rateLimitPause;

        if (topN < 1) {
            throw new IllegalArgumentException("app.github.top-n must be >= 1");
        }
        if (maxLookupsPerRequest < 0) {
            throw new IllegalArgumentException("app.github.max-lookups-per-request must be >= 0");
        }
        if (cacheTtl.isNegative() || cacheTtl.isZero()) {
            throw new IllegalArgumentException("app.github.cache-ttl must be positive");
        }
        if (cacheMaxSize < 1) {
            throw new IllegalArgumentException("app.github.cache-max-size must be >= 1");
        }
        if (rateLimitPause.isNegative()) {
            throw new IllegalArgumentException("app.github.rate-limit-pause must not be negative");
        }
    }

    public static GithubProperties defaults() {
        return new GithubProperties(null, null, null, null, null, null, null, null);
    }
}
