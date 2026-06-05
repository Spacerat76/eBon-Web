package de.ebon.backup;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class BackupRestoreLock {

    private final AtomicBoolean locked = new AtomicBoolean(false);
    private volatile String operation;

    public <T> T runLocked(String operation, Supplier<T> action) {
        if (!locked.compareAndSet(false, true)) {
            throw new BackupRestoreLockedException("Backup oder Restore laeuft bereits.");
        }
        this.operation = operation;
        try {
            return action.get();
        } finally {
            this.operation = null;
            locked.set(false);
        }
    }

    public boolean isLocked() {
        return locked.get();
    }

    public String operation() {
        return operation;
    }
}
