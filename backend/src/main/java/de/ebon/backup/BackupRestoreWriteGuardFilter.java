package de.ebon.backup;

import de.ebon.api.error.ApiErrorFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
public class BackupRestoreWriteGuardFilter extends OncePerRequestFilter {

    private final BackupRestoreLock backupRestoreLock;
    private final ApiErrorFactory apiErrorFactory;
    private final ObjectMapper objectMapper;

    public BackupRestoreWriteGuardFilter(
            BackupRestoreLock backupRestoreLock,
            ApiErrorFactory apiErrorFactory,
            ObjectMapper objectMapper) {
        this.backupRestoreLock = backupRestoreLock;
        this.apiErrorFactory = apiErrorFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isWriteMethod(request.getMethod())
                || request.getRequestURI().startsWith("/api/backup/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!backupRestoreLock.isLocked()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.LOCKED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                apiErrorFactory.create(
                        HttpStatus.LOCKED,
                        "Backup/Restore laeuft; Schreibzugriffe sind voruebergehend gesperrt.",
                        request));
    }

    private boolean isWriteMethod(String method) {
        return "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "DELETE".equals(method);
    }
}
