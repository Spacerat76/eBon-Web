package de.ebon.product;

import de.ebon.api.dto.ProductPriceGrouping;
import java.time.LocalDate;

public record ProductPriceFilter(
        LocalDate dateFrom,
        LocalDate dateTo,
        String store,
        ProductPriceGrouping grouping,
        boolean includeExcluded) {

    public ProductPriceFilter {
        grouping = grouping == null ? ProductPriceGrouping.STORE : grouping;
    }
}
