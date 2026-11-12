package ru.itmo.ipkn.starguard.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.report")
public record ReportProperties(Source source) {

    public enum Source {
        AUTO,
        INDEX,
        GITHUB
    }

    public ReportProperties {
        source = source == null ? Source.AUTO : source;
    }

    public static ReportProperties defaults() {
        return new ReportProperties(null);
    }
}
