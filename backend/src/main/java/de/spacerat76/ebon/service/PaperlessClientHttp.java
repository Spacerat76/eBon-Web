package de.spacerat76.ebon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.spacerat76.ebon.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class PaperlessClientHttp implements PaperlessClient {

    private static final Logger log = LoggerFactory.getLogger(PaperlessClientHttp.class);

    private final RestTemplate restTemplate;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    public PaperlessClientHttp(RestTemplate restTemplate, AppProperties props, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
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
            String url = base + (base.endsWith("/") ? "" : "/") + "api/documents/?tag=" + q;

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (StringUtils.hasText(props.getPaperlessApiToken())) {
                headers.setBearerAuth(props.getPaperlessApiToken());
            }

            HttpEntity<Void> req = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, req, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("Paperless returned non-2xx or empty body when fetching documents: {}", resp.getStatusCode().value());
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(resp.getBody());
            List<Integer> ids = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    JsonNode idNode = node.get("id");
                    if (idNode != null && idNode.canConvertToInt()) {
                        ids.add(idNode.intValue());
                    }
                }
            } else if (root.has("results") && root.get("results").isArray()) {
                for (JsonNode node : root.get("results")) {
                    JsonNode idNode = node.get("id");
                    if (idNode != null && idNode.canConvertToInt()) {
                        ids.add(idNode.intValue());
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
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, req, String.class);
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
