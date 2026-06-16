package de.ebon.system;

import java.util.Optional;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

@Service
public class VersionService {

    private static final String FALLBACK_VERSION = "0.1.0-SNAPSHOT";

    private final Optional<BuildProperties> buildProperties;

    public VersionService(Optional<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    public String version() {
        return buildProperties
                .map(BuildProperties::getVersion)
                .filter(version -> !version.isBlank())
                .orElse(FALLBACK_VERSION);
    }
}
