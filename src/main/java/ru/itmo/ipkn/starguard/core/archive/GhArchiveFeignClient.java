package ru.itmo.ipkn.starguard.core.archive;

import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Декларативный клиент GH Archive. Возвращает сырой streaming-ответ:
 * файл часа большой, и его нельзя буферизовать целиком.
 */
@FeignClient(
        name = "ghArchive",
        url = "${app.archive.base-url}",
        configuration = GhArchiveFeignConfig.class)
public interface GhArchiveFeignClient {

    @GetMapping("/{file}")
    Response download(@PathVariable("file") String file);
}
