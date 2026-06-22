package de.ebon.product;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ProductUnitNormalizer {

    Optional<NormalizedQuantity> normalize(BigDecimal quantity, String unit) {
        if (quantity == null || unit == null || unit.isBlank()) {
            return Optional.empty();
        }
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "ml", "milliliter" -> Optional.of(new NormalizedQuantity(quantity, "ml"));
            case "l", "liter", "ltr" -> Optional.of(new NormalizedQuantity(quantity.multiply(BigDecimal.valueOf(1000)), "ml"));
            case "g", "gramm" -> Optional.of(new NormalizedQuantity(quantity, "g"));
            case "kg", "kilogramm" -> Optional.of(new NormalizedQuantity(quantity.multiply(BigDecimal.valueOf(1000)), "g"));
            case "stk", "st", "stück", "stueck", "piece", "pcs", "pc" -> Optional.of(new NormalizedQuantity(quantity, "piece"));
            default -> Optional.empty();
        };
    }

    record NormalizedQuantity(BigDecimal quantity, String unit) {
    }
}
