package de.ebon.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Gruppierung der Produktpreisvergleiche nach Geschaeft oder Geschaeft und Filiale.")
public enum ProductPriceGrouping {
    STORE,
    STORE_BRANCH
}
