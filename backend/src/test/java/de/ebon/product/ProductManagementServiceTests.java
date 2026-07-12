package de.ebon.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ProductFamilyRepository;
import de.ebon.persistence.repository.ProductRuleRepository;
import de.ebon.persistence.repository.ProductVariantRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductManagementServiceTests {

    @Test
    void familyAndVariantDtosExposeMasterDataCounts() {
        ProductFamilyRepository families = mock(ProductFamilyRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        ReceiptItemRepository items = mock(ReceiptItemRepository.class);
        ProductFamily family = mock(ProductFamily.class);
        ProductVariant variant = mock(ProductVariant.class);
        when(family.getId()).thenReturn(20L);
        when(family.getName()).thenReturn("Haferdrink");
        when(family.isActive()).thenReturn(true);
        when(variant.getId()).thenReturn(31L);
        when(variant.getProductFamily()).thenReturn(family);
        when(variant.getName()).thenReturn("Haferdrink 1 l");
        when(variant.isActive()).thenReturn(true);
        when(families.findAll()).thenReturn(List.of(family));
        when(variants.findAll()).thenReturn(List.of(variant));
        when(variants.countByProductFamily_Id(20L)).thenReturn(3L);
        when(items.countByProductFamily_Id(20L)).thenReturn(42L);
        when(items.countByProductVariant_Id(31L)).thenReturn(18L);

        ProductManagementService service = new ProductManagementService(
                families,
                variants,
                mock(ProductRuleRepository.class),
                mock(CategoryRepository.class),
                items,
                mock(ProductRuleMatcher.class),
                mock(ProductAssignmentService.class));

        var familyDto = service.families().getFirst();
        var variantDto = service.variants(null).getFirst();

        assertThat(familyDto.variantCount()).isEqualTo(3);
        assertThat(familyDto.assignedItemsCount()).isEqualTo(42);
        assertThat(variantDto.assignedItemsCount()).isEqualTo(18);
    }
}
