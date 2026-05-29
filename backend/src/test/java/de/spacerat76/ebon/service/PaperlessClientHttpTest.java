package de.spacerat76.ebon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.spacerat76.ebon.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PaperlessClientHttpTest {

    @Test
    void fetchNewDocumentIds_parsesArrayResponse() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);

        AppProperties props = new AppProperties();
        props.setPaperlessBaseUrl("http://example.com/");
        props.setPaperlessApiToken("tok");
        props.setPaperlessEbonTag("eBON");

        ObjectMapper mapper = new ObjectMapper();
        PaperlessClientHttp client = new PaperlessClientHttp(rt, props, mapper);

        server.expect(MockRestRequestMatchers.requestTo("http://example.com/api/documents/?tags__name=eBON&page_size=100&ordering=-created"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess("[{\"id\":100},{\"id\":101}]", MediaType.APPLICATION_JSON));

        List<Integer> ids = client.fetchNewDocumentIds();

        assertThat(ids).containsExactly(100, 101);
        server.verify();
    }

    @Test
    void fetchNewDocumentIds_parsesPaginatedResults() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);

        AppProperties props = new AppProperties();
        props.setPaperlessBaseUrl("http://example.com");
        props.setPaperlessApiToken("tok");
        props.setPaperlessEbonTag("eBON");

        ObjectMapper mapper = new ObjectMapper();
        PaperlessClientHttp client = new PaperlessClientHttp(rt, props, mapper);

        String body = "{\"count\":2,\"results\":[{\"id\":5},{\"id\":6}]}";

        server.expect(MockRestRequestMatchers.requestTo("http://example.com/api/documents/?tags__name=eBON&page_size=100&ordering=-created"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON));

        List<Integer> ids = client.fetchNewDocumentIds();

        assertThat(ids).containsExactly(5, 6);
        server.verify();
    }

    @Test
    void fetchDocumentText_returnsPlainText() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(rt);

        AppProperties props = new AppProperties();
        props.setPaperlessBaseUrl("http://example.com/");
        props.setPaperlessApiToken("tok");

        ObjectMapper mapper = new ObjectMapper();
        PaperlessClientHttp client = new PaperlessClientHttp(rt, props, mapper);

        server.expect(MockRestRequestMatchers.requestTo("http://example.com/api/documents/123/text/"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess("This is the document text", MediaType.TEXT_PLAIN));

        String text = client.fetchDocumentText(123);

        assertThat(text).isEqualTo("This is the document text");
        server.verify();
    }
}
