package de.ebon.paperless;

import de.ebon.config.PaperlessProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

@Component
class PaperlessRestClient implements PaperlessClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final int PAGE_SIZE = 100;

    private final PaperlessProperties properties;
    private final RestClient restClient;

    PaperlessRestClient(PaperlessProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public List<PaperlessDocument> fetchDocumentsByTag() {
        List<PaperlessDocument> documents = new ArrayList<>();
        String nextUri = firstPagePath();

        while (nextUri != null) {
            PaperlessDocumentPage page = fetchPage(nextUri);
            documents.addAll(page.safeResults().stream()
                    .map(PaperlessDocumentResponse::toDocument)
                    .toList());
            nextUri = page.next() == null || page.next().isBlank()
                    ? null
                    : page.next();
        }

        return documents;
    }

    private String firstPagePath() {
        return "/api/documents/?tags__name__iexact="
                + UriUtils.encodeQueryParam(properties.getEbonTag(), StandardCharsets.UTF_8)
                + "&page_size=" + PAGE_SIZE
                + "&ordering=-created";
    }

    private PaperlessDocumentPage fetchPage(String uri) {
        RestClientException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                PaperlessDocumentPage page = restClient.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, "Token " + properties.getApiToken())
                        .retrieve()
                        .body(PaperlessDocumentPage.class);
                return page == null ? new PaperlessDocumentPage(null, List.of()) : page;
            } catch (RestClientException exception) {
                if (!isRetryable(exception) || attempt == MAX_ATTEMPTS) {
                    throw new PaperlessClientException("Paperless-NGX konnte nicht synchronisiert werden.", exception);
                }
                lastException = exception;
            }
        }
        throw new PaperlessClientException("Paperless-NGX konnte nicht synchronisiert werden.", lastException);
    }

    private boolean isRetryable(RestClientException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatusCode statusCode = responseException.getStatusCode();
            return statusCode.is5xxServerError();
        }
        return true;
    }
}
