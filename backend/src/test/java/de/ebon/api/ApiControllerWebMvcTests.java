package de.ebon.api;

import de.ebon.api.dto.CategoryDto;
import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ReportDto;
import de.ebon.api.dto.SearchResultDto;
import de.ebon.api.service.CategoryApiService;
import de.ebon.api.service.QueryApiService;
import de.ebon.categorization.CategoryDeletionResult;
import de.ebon.support.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.security.api-token=test-token",
        "app.sync.scheduler.enabled=false"
})
class ApiControllerWebMvcTests extends PostgresIntegrationTestSupport {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private QueryApiService queryApiService;

    @Autowired
    private CategoryApiService categoryApiService;

    @BeforeEach
    void resetMocks() {
        // no-op; mocks are stateful only by stubbing in each test
    }

    @Test
    void searchParsesCategoryIdsAndForwardsUnknownSortValues() throws Exception {
        when(queryApiService.search(any(), any(), any(), any(), anyList(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(PageResponse.from(
                        new PageImpl<>(List.of(new SearchResultDto(
                                1L,
                                2L,
                                LocalDate.of(2026, 6, 3),
                                "REWE",
                                "Milch",
                                new BigDecimal("2.49"),
                                4L,
                                "Lebensmittel",
                                List.of("milch")))),
                        "receipt.receiptDate",
                        "desc"));

        HttpResponse<String> response = sendGet("/api/search?q=milch&categoryIds=1,%202,,3&sortBy=unknown&sortDir=desc");

        assertThat(response.statusCode()).isEqualTo(200);
        verify(queryApiService).search(
                eq("milch"),
                eq(null),
                eq(null),
                eq(null),
                eq(List.of(1L, 2L, 3L)),
                eq(null),
                eq(null),
                eq(0),
                eq(20),
                eq("unknown"),
                eq("desc"));
    }

    @Test
    void searchTreatsBlankCategoryIdsAsEmptyList() throws Exception {
        when(queryApiService.search(any(), any(), any(), any(), anyList(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(PageResponse.from(new PageImpl<>(List.of()), "receipt.receiptDate", "desc"));

        HttpResponse<String> response = sendGet("/api/search?categoryIds=%20%20%20");

        assertThat(response.statusCode()).isEqualTo(200);
        verify(queryApiService).search(
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(List.of()),
                eq(null),
                eq(null),
                eq(0),
                eq(20),
                eq("receiptDate"),
                eq("desc"));
    }

    @Test
    void reportsExportEscapesCsvAndParsesCategoryIds() throws Exception {
        when(queryApiService.reportByCategory(any(), any(), anyList(), any()))
                .thenReturn(List.of(
                        new ReportDto.ByCategory(1L, null, new BigDecimal("1.00")),
                        new ReportDto.ByCategory(2L, "Getra\"nke", new BigDecimal("3.50"))));

        HttpResponse<String> response = sendGet("/api/reports/by-category/export?categoryIds=4,%205,,6");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-disposition"))
                .hasValue("attachment; filename=\"report-by-category.csv\"");
        assertThat(response.body()).isEqualTo("""
                categoryId,categoryName,total
                1,,1.00
                2,"Getra""nke",3.50
                """);
        verify(queryApiService).reportByCategory(eq(null), eq(null), eq(List.of(4L, 5L, 6L)), eq(null));
    }

    @Test
    void reportsByPeriodExportTreatsBlankCategoryIdsAsEmptyList() throws Exception {
        when(queryApiService.reportByPeriod(any(), any(), anyList(), any(), anyString()))
                .thenReturn(List.of(new ReportDto.ByPeriod(LocalDate.of(2026, 6, 1), "2026-06", new BigDecimal("2.00"))));

        HttpResponse<String> response = sendGet("/api/reports/by-period/export?categoryIds=%20%20%20");

        assertThat(response.statusCode()).isEqualTo(200);
        verify(queryApiService).reportByPeriod(eq(null), eq(null), eq(List.of()), eq(null), eq("month"));
    }

    @Test
    void categoriesDeleteReturnsHardDeleteMessage() throws Exception {
        when(categoryApiService.deleteOrDeactivate(7L)).thenReturn(CategoryDeletionResult.HARD_DELETED);

        HttpResponse<String> response = sendDelete("/api/categories/7");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"message\":\"Kategorie geloescht.\"}");
    }

    @Test
    void categoriesDeleteReturnsDeactivateMessage() throws Exception {
        when(categoryApiService.deleteOrDeactivate(8L)).thenReturn(CategoryDeletionResult.DEACTIVATED);

        HttpResponse<String> response = sendDelete("/api/categories/8");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"message\":\"Kategorie ist referenziert und wurde deaktiviert.\"}");
    }

    @Test
    void categoriesListForwardsIncludeInactiveFlag() throws Exception {
        when(categoryApiService.list(true)).thenReturn(List.of(new CategoryDto(1L, "Test", "#ffffff", "icon", true, 1, 0)));

        HttpResponse<String> response = sendGet("/api/categories?includeInactive=true");

        assertThat(response.statusCode()).isEqualTo(200);
        verify(categoryApiService).list(true);
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET());
    }

    private HttpResponse<String> sendDelete(String path) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .DELETE());
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return httpClient.send(
                builder.header("Authorization", "Bearer test-token").build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class FakeServiceConfig {

        @Bean
        @Primary
        QueryApiService queryApiService() {
            return mock(QueryApiService.class);
        }

        @Bean
        @Primary
        CategoryApiService categoryApiService() {
            return mock(CategoryApiService.class);
        }
    }
}
