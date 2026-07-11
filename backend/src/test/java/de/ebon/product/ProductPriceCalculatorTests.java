package de.ebon.product;

import static org.assertj.core.api.Assertions.assertThat;

import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.ReceiptItem;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductPriceCalculatorTests {

    @Test
    void derivesComparableLiterPriceFromVariantMultipack() {
        ProductVariant variant = new ProductVariant(
                new ProductFamily("Coca Cola Zero", null),
                "Coca Cola Zero 6 x 0,33 l",
                new BigDecimal("0.330"),
                "l",
                6,
                null,
                null,
                null,
                null);
        ReceiptItem item = new ReceiptItem(0, "Coca Cola Zero", new BigDecimal("5.94"));
        item.assignProduct(variant.getProductFamily(), variant, ProductAssignmentSource.RULE,
                ProductAssignmentStatus.AUTO_ASSIGNED, new BigDecimal("1.000"));

        ProductPriceCalculator.PriceQuote quote = ProductPriceCalculator.quote(item);

        assertThat(quote.effectivePrice()).isEqualByComparingTo("5.94");
        assertThat(quote.normalizedUnit()).isEqualTo("l");
        assertThat(quote.normalizedUnitPrice()).isEqualByComparingTo("3.00");
    }

    @Test
    void derivesRegularPriceOnlyFromAnExplicitPositionDiscount() {
        ReceiptItem item = new ReceiptItem(0, "Aktionsartikel", new BigDecimal("4.49"));
        item.updateParsedValues(null, null, null, new BigDecimal("-0.50"));

        ProductPriceCalculator.PriceQuote quote = ProductPriceCalculator.quote(item);

        assertThat(quote.effectivePrice()).isEqualByComparingTo("4.49");
        assertThat(quote.regularPrice()).isEqualByComparingTo("4.99");
    }

    @Test
    void doesNotInventARegularPriceWithoutAnExplicitDiscount() {
        ReceiptItem item = new ReceiptItem(0, "Normalpreis", new BigDecimal("4.49"));

        assertThat(ProductPriceCalculator.quote(item).regularPrice()).isNull();
    }

    @Test
    void normalizesGramAndPieceUnitsForComparablePrices() {
        assertThat(ProductPriceCalculator.normalizeQuantity(new BigDecimal("500"), "g"))
                .contains(new ProductPriceCalculator.NormalizedQuantity(new BigDecimal("0.500"), "kg"));
        assertThat(ProductPriceCalculator.normalizeQuantity(new BigDecimal("2"), "Stueck"))
                .contains(new ProductPriceCalculator.NormalizedQuantity(new BigDecimal("2"), "Stück"));
    }
}
