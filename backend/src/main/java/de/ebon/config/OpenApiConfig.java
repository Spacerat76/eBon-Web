package de.ebon.config;

import de.ebon.system.VersionService;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "API token",
        scheme = "bearer")
public class OpenApiConfig {

    @Bean
    OpenAPI ebonOpenApi(VersionService versionService) {
        return new OpenAPI()
                .info(new Info()
                        .title("eBon Expense Tracker API")
                        .version(versionService.version()));
    }
}
