package de.ebon.backup;

public class BackupRestoreLockedException extends RuntimeException {

    public BackupRestoreLockedException(String message) {
        super(message);
    }
}
