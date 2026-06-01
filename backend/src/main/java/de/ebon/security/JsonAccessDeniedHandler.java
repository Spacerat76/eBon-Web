package de.ebon.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ebon.api.error.ApiErrorFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorFactory apiErrorFactory;
    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ApiErrorFactory apiErrorFactory, ObjectMapper objectMapper) {
        this.apiErrorFactory = apiErrorFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                apiErrorFactory.create(HttpStatus.FORBIDDEN, "Zugriff verweigert.", request));
    }
}

