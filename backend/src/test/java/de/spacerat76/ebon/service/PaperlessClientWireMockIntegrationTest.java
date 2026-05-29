package de.spacerat76.ebon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import de.spacerat76.ebon.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PaperlessClientWireMockIntegrationTest {

    private WireMockServer wireMockServer;

    @BeforeEach
    void start() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void stop() {
        wireMockServer.stop();
    }

    @Test
    void client_againstWireMock_returnsIdsAndText() {
        // First page with results and a `next` link
        wireMockServer.stubFor(get(urlPathEqualTo("/api/documents/")).withQueryParam("tags__name", equalTo("eBON"))
            .willReturn(aResponse()
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody("{\"count\":4,\"next\":\"" + wireMockServer.baseUrl() + "/api/documents/?page=2\",\"previous\":null,\"results\":[{\"id\":900},{\"id\":901}]}")
            ));

        // Second page
        wireMockServer.stubFor(get(urlPathEqualTo("/api/documents/")).withQueryParam("page", equalTo("2"))
            .willReturn(aResponse()
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody("{\"count\":4,\"next\":null,\"previous\":\"" + wireMockServer.baseUrl() + "/api/documents/\",\"results\":[{\"id\":902},{\"id\":903}]}")
            ));

        wireMockServer.stubFor(get(urlPathEqualTo("/api/documents/900/text/"))
            .willReturn(aResponse()
                .withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .withBody("Text for 900")));
        AppProperties props = new AppProperties();
        props.setPaperlessBaseUrl(wireMockServer.baseUrl());
        props.setPaperlessEbonTag("eBON");

        RestTemplate rt = new RestTemplate();
        ObjectMapper mapper = new ObjectMapper();
        PaperlessClientHttp client = new PaperlessClientHttp(rt, props, mapper);

        List<Integer> ids = client.fetchNewDocumentIds();
        assertThat(ids).containsExactly(900, 901, 902, 903);

        String text = client.fetchDocumentText(900);
        assertThat(text).isEqualTo("Text for 900");
    }
}
