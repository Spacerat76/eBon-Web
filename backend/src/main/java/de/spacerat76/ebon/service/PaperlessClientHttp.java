package de.spacerat76.ebon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.spacerat76.ebon.config.AppProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
// PaperlessClientHttp is instantiated conditionally via PaperlessClientConfig
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class PaperlessClientHttp implements PaperlessClient {

    private static final Logger log = LoggerFactory.getLogger(PaperlessClientHttp.class);

    private final RestTemplate restTemplate;
    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public PaperlessClientHttp(RestTemplate restTemplate, AppProperties props, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .build();
        this.circuitBreaker = CircuitBreaker.of("paperless", cbConfig);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryExceptions(RestClientException.class)
                .build();
        this.retry = Retry.of("paperless", retryConfig);
    }

    @Override
    public List<Integer> fetchNewDocumentIds() {
        String base = props.getPaperlessBaseUrl();
        if (!StringUtils.hasText(base)) {
            log.debug("Paperless base URL not configured");
            return Collections.emptyList();
        }
        String tag = props.getPaperlessEbonTag();
        try {
            String q = URLEncoder.encode(tag == null ? "" : tag, StandardCharsets.UTF_8);
            // Request first page with reasonable page size and ordering
            String url = base + (base.endsWith("/") ? "" : "/") + "api/documents/?tags__name=" + q + "&page_size=100&ordering=-created";

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (StringUtils.hasText(props.getPaperlessApiToken())) {
                headers.setBearerAuth(props.getPaperlessApiToken());
            }

            List<Integer> ids = new ArrayList<>();

            while (url != null) {
                HttpEntity<Void> req = new HttpEntity<>(headers);

                String currentUrl = url;
                Supplier<ResponseEntity<String>> supplier = () -> restTemplate.exchange(currentUrl, HttpMethod.GET, req, String.class);
                Supplier<ResponseEntity<String>> decorated = io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
                decorated = io.github.resilience4j.retry.Retry.decorateSupplier(retry, decorated);

                ResponseEntity<String> resp = decorated.get();

                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                    log.warn("Paperless returned non-2xx or empty body when fetching documents: {}", resp.getStatusCode().value());
                    break;
                }

                JsonNode root = objectMapper.readTree(resp.getBody());

                if (root.isArray()) {
                    for (JsonNode node : root) {
                        JsonNode idNode = node.get("id");
                        if (idNode != null && idNode.canConvertToInt()) {
                            ids.add(idNode.intValue());
                        }
                    }
                    // No pagination metadata => stop
                    url = null;
                } else {
                    if (root.has("results") && root.get("results").isArray()) {
                        for (JsonNode node : root.get("results")) {
                            JsonNode idNode = node.get("id");
                            if (idNode != null && idNode.canConvertToInt()) {
                                ids.add(idNode.intValue());
                            }
                        }
                    }
                    if (root.has("next") && !root.get("next").isNull()) {
                        String next = root.get("next").asText();
                        url = (next == null || next.isBlank()) ? null : next;
                    } else {
                        url = null;
                    }
                }
            }

            return ids;
        } catch (RestClientException ex) {
            log.warn("Error calling Paperless API for document ids", ex);
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Error parsing Paperless response", ex);
            return Collections.emptyList();
        }
    }

    @Override
    public String fetchDocumentText(Integer documentId) {
        String base = props.getPaperlessBaseUrl();
        if (!StringUtils.hasText(base) || documentId == null) {
            return null;
        }

        try {
            String url = base + (base.endsWith("/") ? "" : "/") + "api/documents/" + documentId + "/text/";
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
            if (StringUtils.hasText(props.getPaperlessApiToken())) {
                headers.setBearerAuth(props.getPaperlessApiToken());
            }
            HttpEntity<Void> req = new HttpEntity<>(headers);
            Supplier<ResponseEntity<String>> supplier = () -> restTemplate.exchange(url, HttpMethod.GET, req, String.class);
            Supplier<ResponseEntity<String>> decorated = io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
            decorated = io.github.resilience4j.retry.Retry.decorateSupplier(retry, decorated);

            ResponseEntity<String> resp = decorated.get();
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("Paperless returned non-2xx when fetching document text: {}", resp.getStatusCode().value());
                return null;
            }
            return resp.getBody();
        } catch (Exception ex) {
            log.warn("Error fetching document text from Paperless", ex);
            return null;
        }
    }
}
