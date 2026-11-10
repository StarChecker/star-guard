package ru.itmo.ipkn.starguard.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI starGuardOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Star Guard Core PoC")
                        .version("0.1.0")
                        .description("""
                                Статистика репозитория по данным GH Archive и suspicious score.
                                В PoC проверки C1–C5 замоканы: в ответе score.mocked = true,
                                профиль моков выбирается параметром mock_mode.
                                """));
    }
}
