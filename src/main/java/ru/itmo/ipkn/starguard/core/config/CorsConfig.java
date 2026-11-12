package ru.itmo.ipkn.starguard.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS для публичного API: адрес сервера расширение и лендинг берут из server.json во время работы,
 * поэтому заранее выдать браузеру разрешение на этот домен нельзя — запросы идут как обычные
 * кросс-доменные. API только читает публичные данные, cookie и авторизации нет.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
