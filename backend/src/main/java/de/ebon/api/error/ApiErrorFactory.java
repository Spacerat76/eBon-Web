package de.ebon.api.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorFactory {

    private final Clock clock;

    public ApiErrorFactory(Clock clock) {
        this.clock = clock;
    }

    public ApiError create(HttpStatus status, String message, HttpServletRequest request) {
        Object traceId = request.getAttribute("traceId");
        String resolvedTraceId = traceId == null || traceId.toString().isBlank()
                ? UUID.randomUUID().toString()
                : traceId.toString();
        return new ApiError(
                status.value(),
                status.getReasonPhrase(),
                message,
                clock.instant(),
                request.getRequestURI(),
                resolvedTraceId);
    }
}
