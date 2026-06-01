package de.ebon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openapi")
public record AppOpenApiProperties(boolean publicAccess) {
}
