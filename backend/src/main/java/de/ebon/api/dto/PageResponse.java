package de.ebon.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "Einheitliches paginiertes Antwortformat.")
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sortBy,
        String sortDir) {

    public static <T> PageResponse<T> from(Page<T> page, String sortBy, String sortDir) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                sortBy,
                sortDir);
    }
}
