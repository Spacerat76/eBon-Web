package de.spacerat76.ebon.service;

import de.spacerat76.ebon.domain.Category;
import de.spacerat76.ebon.domain.Receipt;
import de.spacerat76.ebon.domain.ReceiptItem;
import de.spacerat76.ebon.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CategorizationServiceTest {

    @Mock
    CategoryRepository categoryRepository;
    @Mock
    de.spacerat76.ebon.repository.CategorizationRuleRepository categorizationRuleRepository;

    @InjectMocks
    CategorizationServiceImpl categorizationService;

    @Test
    void categorize_createsCategoryWhenNotFoundAndAssignsToItem() {
        Receipt receipt = new Receipt();
        receipt.setRawText("Bought at Supermarket XYZ");
        ReceiptItem item = new ReceiptItem();
        item.setPositionIndex(1);
        item.setDescription("Milk");
        item.setTotalPrice(new BigDecimal("1.23"));
        receipt.addItem(item);

        when(categoryRepository.findByName("Groceries")).thenReturn(Optional.empty());
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        categorizationService.categorize(receipt);

        assertThat(receipt.getItems()).isNotEmpty();
        assertThat(receipt.getItems().get(0).getCategory()).isNotNull();
        assertThat(receipt.getItems().get(0).getCategory().getName()).isEqualTo("Groceries");
        assertThat(receipt.getItems().get(0).getCategorySource()).isEqualTo("AUTOMATIC");

        verify(categoryRepository).save(any());
    }

    @Test
    void categorize_appliesRuleAndAssignsCategory() {
        Receipt receipt = new Receipt();
        receipt.setRawText("Store X");
        ReceiptItem item = new ReceiptItem();
        item.setPositionIndex(1);
        item.setDescription("Chocolate Bar");
        item.setTotalPrice(new BigDecimal("1.20"));
        receipt.addItem(item);

        de.spacerat76.ebon.domain.CategorizationRule rule = new de.spacerat76.ebon.domain.CategorizationRule();
        rule.setId(1L);
        rule.setName("Chocolate rule");
        rule.setPattern("chocolate|choco");
        rule.setCategoryId(99L);
        rule.setPriority(100);

        when(categorizationRuleRepository.findAll()).thenReturn(java.util.List.of(rule));

        de.spacerat76.ebon.domain.Category cat = new de.spacerat76.ebon.domain.Category();
        cat.setId(99L);
        cat.setName("Snacks");
        cat.setIsActive(true);

        when(categoryRepository.findById(99L)).thenReturn(Optional.of(cat));

        categorizationService.categorize(receipt);

        assertThat(receipt.getItems().get(0).getCategory()).isNotNull();
        assertThat(receipt.getItems().get(0).getCategory().getName()).isEqualTo("Snacks");
        assertThat(receipt.getItems().get(0).getCategorySource()).isEqualTo("RULE");
    }
}
