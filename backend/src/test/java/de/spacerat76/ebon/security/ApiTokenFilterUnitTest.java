package de.spacerat76.ebon.security;

import de.spacerat76.ebon.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ApiTokenFilterUnitTest {

    @Test
    void validTokenAllowsChain() throws ServletException, IOException {
        AppProperties props = new AppProperties();
        props.setAppApiToken("secret");
        ApiTokenFilter filter = new ApiTokenFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        req.addHeader("X-API-TOKEN", "secret");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean invoked = new AtomicBoolean(false);
        FilterChain chain = (request, response) -> invoked.set(true);

        filter.doFilter(req, res, chain);

        assertTrue(invoked.get(), "Filter chain should have been invoked for valid token");
    }

    @Test
    void invalidTokenReturns401() throws ServletException, IOException {
        AppProperties props = new AppProperties();
        props.setAppApiToken("topsecret");
        ApiTokenFilter filter = new ApiTokenFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        req.addHeader("X-API-TOKEN", "wrong");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean invoked = new AtomicBoolean(false);
        FilterChain chain = (request, response) -> invoked.set(true);

        filter.doFilter(req, res, chain);

        assertFalse(invoked.get(), "Filter chain should NOT be invoked for invalid token");
        assertEquals(401, res.getStatus());
    }

    @Test
    void bearerAuthorizationHeaderWorks() throws ServletException, IOException {
        AppProperties props = new AppProperties();
        props.setAppApiToken("bearer-secret");
        ApiTokenFilter filter = new ApiTokenFilter(props);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/test");
        req.addHeader("Authorization", "Bearer bearer-secret");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean invoked = new AtomicBoolean(false);
        FilterChain chain = (request, response) -> invoked.set(true);

        filter.doFilter(req, res, chain);

        assertTrue(invoked.get(), "Filter chain should be invoked for valid bearer token");
    }

    @Test
    void actuatorPathBypassesFilter() throws ServletException, IOException {
        AppProperties props = new AppProperties();
        // no token configured -> ApiTokenFilter will return 401 if applied; but shouldNotFilter should bypass
        AppProperties emptyProps = new AppProperties();
        ApiTokenFilter filter = new ApiTokenFilter(emptyProps);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse res = new MockHttpServletResponse();

        AtomicBoolean invoked = new AtomicBoolean(false);
        FilterChain chain = (request, response) -> invoked.set(true);

        filter.doFilter(req, res, chain);

        assertTrue(invoked.get(), "Actuator path should bypass token filter");
    }
}
