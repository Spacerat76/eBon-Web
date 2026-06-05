package de.ebon.api;

import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.SearchResultDto;
import de.ebon.api.service.QueryApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Suche")
@SecurityRequirement(name = "bearerAuth")
public class SearchController {

    private final QueryApiService queryApiService;

    public SearchController(QueryApiService queryApiService) {
        this.queryApiService = queryApiService;
    }

    @GetMapping("/api/search")
    @Operation(summary = "Bons und Positionen durchsuchen")
    public PageResponse<SearchResultDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String store,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String categoryIds,
            @RequestParam(defaultValue = "false") boolean uncategorizedOnly,
            @RequestParam(required = false) @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amountMin,
            @RequestParam(required = false) @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amountMax,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "receiptDate") String sortBy,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc") String sortDir) {
        return queryApiService.search(
                q,
                store,
                dateFrom,
                dateTo,
                parseIds(categoryIds),
                uncategorizedOnly,
                amountMin,
                amountMax,
                page,
                size,
                sortBy,
                sortDir);
    }

    private List<Long> parseIds(String categoryIds) {
        if (categoryIds == null || categoryIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(categoryIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::parseLong)
                .toList();
    }
}
