package de.ebon.api.error;

import java.time.Instant;

public record ApiError(
        int status,
        String error,
        String message,
        Instant timestamp,
        String path,
        String traceId) {
}

