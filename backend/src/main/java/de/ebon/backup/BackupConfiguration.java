package de.ebon.backup;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RollingBackupProperties.class)
public class BackupConfiguration {
}
