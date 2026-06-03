package de.ebon.api.error;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.MethodParameter;
import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorHandlingTests {

    @Test
    void factoryUsesProvidedTraceIdOrGeneratesANewOne() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-03T05:00:00Z"), ZoneOffset.UTC);
        ApiErrorFactory factory = new ApiErrorFactory(clock);

        MockHttpServletRequest requestWithTraceId = new MockHttpServletRequest();
        requestWithTraceId.setRequestURI("/api/test");
        requestWithTraceId.setAttribute("traceId", "trace-123");

        ApiError withTrace = factory.create(HttpStatus.BAD_REQUEST, "Fehler", requestWithTraceId);
        assertThat(withTrace.status()).isEqualTo(400);
        assertThat(withTrace.error()).isEqualTo("Bad Request");
        assertThat(withTrace.message()).isEqualTo("Fehler");
        assertThat(withTrace.timestamp()).isEqualTo(Instant.parse("2026-06-03T05:00:00Z"));
        assertThat(withTrace.path()).isEqualTo("/api/test");
        assertThat(withTrace.traceId()).isEqualTo("trace-123");

        MockHttpServletRequest requestWithoutTraceId = new MockHttpServletRequest();
        requestWithoutTraceId.setRequestURI("/api/test-2");
        requestWithoutTraceId.setAttribute("traceId", "   ");

        ApiError generatedTrace = factory.create(HttpStatus.NOT_FOUND, "Nicht gefunden", requestWithoutTraceId);
        assertThat(generatedTrace.status()).isEqualTo(404);
        assertThat(generatedTrace.traceId()).isNotBlank();
        assertThat(generatedTrace.path()).isEqualTo("/api/test-2");
    }

    @Test
    void validationHandlerUsesFieldErrorAndFallsBackWhenNoFieldErrorExists() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new ApiErrorFactory(Clock.fixed(
                Instant.parse("2026-06-03T05:00:00Z"), ZoneOffset.UTC)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/items");
        request.setAttribute("traceId", "trace-abc");

        MethodArgumentNotValidException withFieldError = methodArgumentNotValidException("name", "darf nicht leer sein");
        assertThat(handler.handleValidation(withFieldError, request).getBody().message())
                .isEqualTo("Feld 'name' darf nicht leer sein");

        MethodArgumentNotValidException withoutFieldError = methodArgumentNotValidException(null, null);
        assertThat(handler.handleValidation(withoutFieldError, request).getBody().message())
                .isEqualTo("Validierungsfehler im Request.");
    }

    @Test
    void responseStatusHandlerUsesReasonWhenPresentAndStatusPhraseOtherwise() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new ApiErrorFactory(Clock.fixed(
                Instant.parse("2026-06-03T05:00:00Z"), ZoneOffset.UTC)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/items");
        request.setAttribute("traceId", "trace-abc");

        assertThat(handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "Benutzerdefinierte Meldung"),
                request).getBody().message()).isEqualTo("Benutzerdefinierte Meldung");

        assertThat(handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT),
                request).getBody().message()).isEqualTo("Conflict");
    }

    @Test
    void requestValidationAndFallbackHandlersReturnExpectedApiErrors() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new ApiErrorFactory(Clock.fixed(
                Instant.parse("2026-06-03T05:00:00Z"), ZoneOffset.UTC)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/items");
        request.setAttribute("traceId", "trace-abc");
        assertThat(handler.handleRequestValidation(
                new ConstraintViolationException(java.util.Set.of()),
                request).getBody().status()).isEqualTo(400);
        assertThat(handler.handleNotFound(new jakarta.persistence.EntityNotFoundException("Bon nicht gefunden."), request)
                .getBody().message()).isEqualTo("Bon nicht gefunden.");
        assertThat(handler.handleConflict(new org.springframework.dao.DataIntegrityViolationException("x"), request)
                .getBody().message()).isEqualTo("Datenkonflikt beim Speichern.");
        assertThat(handler.handleBadRequest(new IllegalArgumentException("Ungueltig"), request).getBody().message())
                .isEqualTo("Ungueltig");
        assertThat(handler.handleUnexpected(new RuntimeException("boom"), request).getBody().message())
                .isEqualTo("Unerwarteter Fehler.");
    }

    private MethodArgumentNotValidException methodArgumentNotValidException(String fieldName, String defaultMessage)
            throws Exception {
        Method method = DummyEndpoint.class.getDeclaredMethod("endpoint", String.class);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new DummyEndpoint(), "dummy");
        if (fieldName != null) {
            bindingResult.addError(new FieldError("dummy", fieldName, defaultMessage));
        }
        return new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(method, 0),
                bindingResult);
    }

    static class DummyEndpoint {
        @SuppressWarnings("unused")
        public void endpoint(String value) {
        }
    }
}
