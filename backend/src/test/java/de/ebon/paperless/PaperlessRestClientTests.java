package de.ebon.paperless;

import de.ebon.config.PaperlessProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaperlessRestClientTests {

    @Test
    void fetchesAllPagesWithTokenAuthentication() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://paperless");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaperlessRestClient client = new PaperlessRestClient(properties(), builder);

        server.expect(requestTo("http://paperless/api/documents/?tags__name__iexact=eBON&page_size=100&ordering=-created"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Token test-paperless-token"))
                .andRespond(withSuccess("""
                        {
                          "next": "http://paperless/api/documents/?page=2",
                          "results": [
                            {
                              "id": 1,
                              "title": "First",
                              "created": "2026-01-01T10:00:00Z",
                              "content": "first raw"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://paperless/api/documents/?page=2"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Token test-paperless-token"))
                .andRespond(withSuccess("""
                        {
                          "next": null,
                          "results": [
                            {
                              "id": 2,
                              "title": "Second",
                              "created": "2026-01-02T10:00:00Z",
                              "content": "second raw"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<PaperlessDocument> documents = client.fetchDocumentsByTag();

        assertThat(documents).extracting(PaperlessDocument::id).containsExactly(1, 2);
        assertThat(documents).extracting(PaperlessDocument::content).containsExactly("first raw", "second raw");
        server.verify();
    }

    @Test
    void retriesServerErrorsBeforeFailing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://paperless");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaperlessRestClient client = new PaperlessRestClient(properties(), builder);

        server.expect(
                        ExpectedCount.times(3),
                        requestTo("http://paperless/api/documents/?tags__name__iexact=eBON&page_size=100&ordering=-created"))
                .andRespond(withServerError());

        assertThatThrownBy(client::fetchDocumentsByTag)
                .isInstanceOf(PaperlessClientException.class);
        server.verify();
    }

    @Test
    void acceptsPaperlessDateOnlyCreatedField() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://paperless");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaperlessRestClient client = new PaperlessRestClient(properties(), builder);

        server.expect(requestTo("http://paperless/api/documents/?tags__name__iexact=eBON&page_size=100&ordering=-created"))
                .andRespond(withSuccess("""
                        {
                          "next": null,
                          "results": [
                            {
                              "id": 3,
                              "title": "Date only",
                              "created": "2026-06-03",
                              "content": "date only raw"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<PaperlessDocument> documents = client.fetchDocumentsByTag();

        assertThat(documents).singleElement()
                .satisfies(document -> {
                    assertThat(document.id()).isEqualTo(3);
                    assertThat(document.created()).isEqualTo("2026-06-03");
                });
        server.verify();
    }

    private static PaperlessProperties properties() {
        PaperlessProperties properties = new PaperlessProperties();
        properties.setBaseUrl("http://paperless");
        properties.setApiToken("test-paperless-token");
        properties.setEbonTag("eBON");
        return properties;
    }
}
