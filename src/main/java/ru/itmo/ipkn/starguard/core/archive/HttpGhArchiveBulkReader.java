package ru.itmo.ipkn.starguard.core.archive;

import feign.Response;
import feign.RetryableException;
import ru.itmo.ipkn.starguard.core.domain.GhEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Читает часовой gzip-дамп GH Archive и передаёт все события в sink.
 * Ретраи и обработка частичных результатов — на стороне вызывающего.
 */
public class HttpGhArchiveBulkReader implements GhArchiveBulkReader {

    private static final DateTimeFormatter HOUR_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-H", Locale.ROOT);

    private final GhArchiveFeignClient feignClient;
    private final GhArchiveParser parser;

    public HttpGhArchiveBulkReader(GhArchiveFeignClient feignClient, GhArchiveParser parser) {
        this.feignClient = feignClient;
        this.parser = parser;
    }

    @Override
    public HourReadResult readHour(Instant hour, Consumer<GhEvent> sink) {
        String file = HOUR_FORMAT.format(hour.atZone(ZoneOffset.UTC)) + ".json.gz";
        try (Response response = feignClient.download(file)) {
            if (response.status() == 404) {
                return new HourReadResult(hour, 0, true, false);
            }
            if (response.status() < 200 || response.status() >= 300) {
                throw new TransientArchiveException("GH Archive returned status " + response.status());
            }
            if (response.body() == null) {
                throw new TransientArchiveException("GH Archive returned an empty body");
            }

            int parseErrors = 0;
            Instant hourEnd = hour.plusSeconds(3600);
            try (InputStream body = response.body().asInputStream();
                 GZIPInputStream gzip = new GZIPInputStream(body);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        Optional<GhEvent> parsed = parser.parse(line);
                        if (parsed.isPresent()) {
                            GhEvent event = parsed.get();
                            if (!event.createdAt().isBefore(hour)
                                    && event.createdAt().isBefore(hourEnd)) {
                                sink.accept(event);
                            }
                        }
                    } catch (MalformedEventException e) {
                        parseErrors++;
                    }
                }
            }
            return new HourReadResult(hour, parseErrors, false, false);
        } catch (IOException | RetryableException e) {
            throw new TransientArchiveException("I/O error while fetching " + file, e);
        }
    }
}
