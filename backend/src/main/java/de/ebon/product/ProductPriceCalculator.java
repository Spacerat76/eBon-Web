package de.ebon.product;

import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.ReceiptItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/**
 * Derives comparable product prices from persisted receipt and product data.
 * Unknown units deliberately remain incomparable instead of being guessed.
 */
public final class ProductPriceCalculator {

    private static final int PRICE_SCALE = 4;

    private ProductPriceCalculator() {
    }

    public static PriceQuote quote(ReceiptItem item) {
        BigDecimal effectivePrice = item.getTotalPrice();
        BigDecimal regularPrice = regularPrice(item);
        Optional<NormalizedQuantity> quantity = comparableQuantity(item);
        BigDecimal normalizedUnitPrice = effectivePrice == null || quantity.isEmpty()
                ? null
                : effectivePrice.divide(quantity.get().quantity(), PRICE_SCALE, RoundingMode.HALF_UP);

        return new PriceQuote(
                effectivePrice,
                regularPrice,
                normalizedUnitPrice,
                quantity.map(NormalizedQuantity::unit).orElse(null));
    }

    /**
     * Normalizes only bases that are safe to compare across products: litre, kilogram, and piece.
     */
    public static Optional<NormalizedQuantity> normalizeQuantity(BigDecimal quantity, String unit) {
        if (quantity == null || quantity.signum() <= 0 || unit == null || unit.isBlank()) {
            return Optional.empty();
        }
        return switch (unit.trim().toLowerCase(Locale.ROOT)) {
            case "ml", "milliliter" -> Optional.of(new NormalizedQuantity(quantity.movePointLeft(3), "l"));
            case "l", "liter", "ltr" -> Optional.of(new NormalizedQuantity(quantity, "l"));
            case "g", "gramm" -> Optional.of(new NormalizedQuantity(quantity.movePointLeft(3), "kg"));
            case "kg", "kilogramm" -> Optional.of(new NormalizedQuantity(quantity, "kg"));
            case "stk", "st", "stück", "stueck", "piece", "pcs", "pc" ->
                Optional.of(new NormalizedQuantity(quantity, "Stück"));
            default -> Optional.empty();
        };
    }

    private static Optional<NormalizedQuantity> comparableQuantity(ReceiptItem item) {
        ProductVariant variant = item.getProductVariant();
        if (variant != null) {
            Optional<NormalizedQuantity> variantQuantity = normalizeQuantity(
                    variant.getTotalQuantity(), variant.getTotalUnit());
            if (variantQuantity.isPresent()) {
                return variantQuantity;
            }
        }
        return normalizeQuantity(item.getQuantity(), item.getUnit());
    }

    private static BigDecimal regularPrice(ReceiptItem item) {
        BigDecimal discount = item.getDiscountAmount();
        if (item.getTotalPrice() == null || discount == null || discount.signum() >= 0) {
            return null;
        }
        return item.getTotalPrice().add(discount.abs());
    }

    public record NormalizedQuantity(BigDecimal quantity, String unit) {
    }

    public record PriceQuote(
            BigDecimal effectivePrice,
            BigDecimal regularPrice,
            BigDecimal normalizedUnitPrice,
            String normalizedUnit) {
    }
}
