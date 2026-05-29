package de.spacerat76.ebon.security;

import de.spacerat76.ebon.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiTokenFilter extends OncePerRequestFilter {
    private final AppProperties appProperties;

    public ApiTokenFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // Allow actuator endpoints unauthenticated for health checks
        return path.startsWith("/actuator/") || path.equals("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String configured = appProperties.getAppApiToken();
        if (configured == null || configured.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "API token not configured");
            return;
        }

        String token = request.getHeader("X-API-TOKEN");
        if (token == null || token.isBlank()) {
            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring(7).trim();
            }
        }

        if (configured.equals(token)) {
            filterChain.doFilter(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API token");
        }
    }
}
