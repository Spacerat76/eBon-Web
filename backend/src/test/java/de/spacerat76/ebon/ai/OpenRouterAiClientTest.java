package de.spacerat76.ebon.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.spacerat76.ebon.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenRouterAiClientTest {

    @Test
    void parseReceipt_parsesJsonContent_and_cost() throws Exception {
        // Start a simple local HTTP server to simulate OpenRouter
        com.sun.net.httpserver.HttpServer http = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        String assistantContent = "{\"storeName\":\"MyStore\",\"totalAmount\":15.5,\"receiptDate\":\"2026-05-28\",\"items\":[{\"description\":\"Milk\",\"quantity\":1,\"unit\":\"pcs\",\"unitPrice\":15.5,\"total\":15.5}]}";
        String body = mapper.writeValueAsString(Map.of(
            "choices", List.of(Map.of("message", Map.of("content", mapper.readTree(assistantContent)))),
            "usage", Map.of("total_cost", new BigDecimal("0.003"))
        ));

        http.createContext("/v1/chat/completions", exchange -> {
            // debug: print request
            try (java.io.InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            } catch (Exception ignore) {}
            byte[] resp = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        http.start();

        RestTemplate rt = new RestTemplate();
        AppProperties props = new AppProperties();
        props.setOpenrouterBaseUrl("http://localhost:" + http.getAddress().getPort());
        props.setOpenrouterApiKey("tok");
        props.setOpenrouterModel("gpt-test");
        props.setAiRetryMaxAttempts(2);
        props.setAiRetryInitialWaitMs(100);

        OpenRouterAiClient client = new OpenRouterAiClient(rt, props, mapper);

        Optional<AiParseResult> res = client.parseReceipt("dummy receipt text");

        assertThat(res).isPresent();
        AiParseResult r = res.get();
        assertThat(r.getStoreName()).isEqualTo("MyStore");
        assertThat(r.getTotalAmount()).isEqualByComparingTo(new BigDecimal("15.5"));
        assertThat(r.getItems()).hasSize(1);
        assertThat(r.getCost()).isEqualByComparingTo(new BigDecimal("0.003"));
        http.stop(0);
    }

    @Test
    void parseReceipt_retriesOn500AndSucceeds() throws Exception {
        // Use a small HttpServer to simulate one failed call followed by a success
        com.sun.net.httpserver.HttpServer http = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        String assistantContent = "{\"storeName\":\"RetryStore\",\"totalAmount\":5.0}";
        String body = mapper.writeValueAsString(Map.of(
            "choices", List.of(Map.of("message", Map.of("content", mapper.readTree(assistantContent))))
        ));

        final int[] calls = {0};
        http.createContext("/v1/chat/completions", exchange -> {
            calls[0]++;
            try (java.io.InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            } catch (Exception ignore) {}
            if (calls[0] == 1) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            byte[] resp = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        http.start();

        RestTemplate rt = new RestTemplate();
        AppProperties props = new AppProperties();
        props.setOpenrouterBaseUrl("http://localhost:" + http.getAddress().getPort());
        props.setOpenrouterApiKey("tok");
        props.setOpenrouterModel("gpt-test");
        props.setAiRetryMaxAttempts(3);
        props.setAiRetryInitialWaitMs(50);

        OpenRouterAiClient client = new OpenRouterAiClient(rt, props, mapper);

        Optional<AiParseResult> res = client.parseReceipt("dummy receipt text");

        assertThat(res).isPresent();
        assertThat(res.get().getStoreName()).isEqualTo("RetryStore");
        http.stop(0);
    }
}
