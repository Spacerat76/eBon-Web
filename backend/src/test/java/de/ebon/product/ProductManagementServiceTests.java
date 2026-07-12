package de.ebon.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.IdCount;
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
        ProductFamily secondFamily = mock(ProductFamily.class);
        ProductVariant variant = mock(ProductVariant.class);
        ProductVariant secondVariant = mock(ProductVariant.class);
        when(family.getId()).thenReturn(20L);
        when(family.getName()).thenReturn("Haferdrink");
        when(family.isActive()).thenReturn(true);
        when(secondFamily.getId()).thenReturn(21L);
        when(secondFamily.getName()).thenReturn("Sojadrink");
        when(secondFamily.isActive()).thenReturn(true);
        when(variant.getId()).thenReturn(31L);
        when(variant.getProductFamily()).thenReturn(family);
        when(variant.getName()).thenReturn("Haferdrink 1 l");
        when(variant.isActive()).thenReturn(true);
        when(secondVariant.getId()).thenReturn(32L);
        when(secondVariant.getProductFamily()).thenReturn(secondFamily);
        when(secondVariant.getName()).thenReturn("Sojadrink 1 l");
        when(secondVariant.isActive()).thenReturn(true);
        when(families.findAll()).thenReturn(List.of(family, secondFamily));
        when(variants.findAll()).thenReturn(List.of(variant, secondVariant));
        when(variants.countGroupedByProductFamily()).thenReturn(List.of(new IdCount(20L, 3L), new IdCount(21L, 1L)));
        when(items.countGroupedByProductFamily()).thenReturn(List.of(new IdCount(20L, 42L), new IdCount(21L, 7L)));
        when(items.countGroupedByProductVariant()).thenReturn(List.of(new IdCount(31L, 18L), new IdCount(32L, 7L)));

        ProductManagementService service = new ProductManagementService(
                families,
                variants,
                mock(ProductRuleRepository.class),
                mock(CategoryRepository.class),
                items,
                mock(ProductRuleMatcher.class),
                mock(ProductAssignmentService.class));

        var familyDtos = service.families();
        var variantDtos = service.variants(null);
        var familyDto = familyDtos.getFirst();
        var variantDto = variantDtos.getFirst();

        assertThat(familyDto.variantCount()).isEqualTo(3);
        assertThat(familyDto.assignedItemsCount()).isEqualTo(42);
        assertThat(variantDto.assignedItemsCount()).isEqualTo(18);
        assertThat(familyDtos.get(1).assignedItemsCount()).isEqualTo(7);
        assertThat(variantDtos.get(1).assignedItemsCount()).isEqualTo(7);
        verify(variants).countGroupedByProductFamily();
        verify(items).countGroupedByProductFamily();
        verify(items).countGroupedByProductVariant();
        verify(variants, never()).countByProductFamily_Id(20L);
        verify(items, never()).countByProductFamily_Id(20L);
        verify(items, never()).countByProductVariant_Id(31L);
        verify(variants, never()).countByProductFamily_Id(21L);
        verify(items, never()).countByProductFamily_Id(21L);
        verify(items, never()).countByProductVariant_Id(32L);
    }
}
