package de.ebon.backup;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.backup.rolling")
public class RollingBackupProperties {

    private boolean enabled = false;
    private Path directory = Path.of("backups", "automatic");
    @NotBlank
    private String cron = "0 0 3 * * *";
    @Min(1)
    private int retentionCount = 7;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        if (directory != null) {
            this.directory = directory;
        }
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        if (cron != null) {
            this.cron = cron;
        }
    }

    public int getRetentionCount() {
        return retentionCount;
    }

    public void setRetentionCount(int retentionCount) {
        this.retentionCount = retentionCount;
    }
}
