package de.ebon.api.service;

import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.ReceiptDto;
import de.ebon.api.dto.ReceiptItemCreateRequest;
import de.ebon.api.dto.ReceiptItemDto;
import de.ebon.api.dto.ReceiptItemUpdateRequest;
import de.ebon.api.dto.ReceiptUpdateRequest;
import de.ebon.categorization.CategorizationService;
import de.ebon.parser.ParsedReceipt;
import de.ebon.parser.ReceiptParseApplier;
import de.ebon.parser.ReceiptParseResult;
import de.ebon.parser.ReceiptParserService;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.AiCategorizationLogRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptApiServiceTests {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private ReceiptItemRepository receiptItemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AiCategorizationLogRepository aiCategorizationLogRepository;

    @Mock
    private CategorizationService categorizationService;

    @Mock
    private ReceiptParserService receiptParserService;

    @Mock
    private ReceiptParseApplier receiptParseApplier;

    private final ReceiptParseApplier realReceiptParseApplier = new ReceiptParseApplier();

    private ReceiptApiService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptApiService(
                receiptRepository,
                receiptItemRepository,
                categoryRepository,
                aiCategorizationLogRepository,
                categorizationService,
                receiptParserService,
                realReceiptParseApplier);
    }

    @Test
    void listReceiptsFallsBackToDefaultSortAndClampsPageSize() {
        Receipt receipt = receipt(1L, 1001, "REWE", false, "Bio Milch");
        when(receiptRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(receipt)));

        PageResponse<?> result = service.listReceipts(
                -3,
                500,
                "unknown-sort",
                "up",
                null,
                null,
                null,
                "  ",
                false);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(receiptRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertThat(result.sortBy()).isEqualTo("receiptDate");
        assertThat(result.sortDir()).isEqualTo("desc");
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("receiptDate").getDirection().name()).isEqualTo("DESC");
        assertThat(pageable.getSort().getOrderFor("id").getDirection().name()).isEqualTo("DESC");
    }

    @Test
    void getReceiptRejectsMissingOrDeletedReceipts() {
        when(receiptRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReceipt(1L))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessage("Bon nicht gefunden.");

        Receipt deleted = receipt(2L, 1002, "REWE", true, "Gelöscht");
        when(receiptRepository.findById(2L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.getReceipt(2L))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessage("Bon nicht gefunden.");
    }

    @Test
    void updateItemSupportsClearingAndAssigningCategories() {
        Category drogerie = category(11L, "Drogerie");
        Receipt receipt = receipt(10L, 2001, "dm", false, "Shampoo");
        ReceiptItem item = firstItem(receipt);
        item.assignCategory(drogerie, CategorySource.RULE);

        when(receiptItemRepository.findById(item.getId())).thenAnswer(invocation -> Optional.of(item));

        doAnswer(invocation -> {
            item.manuallyClearCategory();
            return null;
        }).when(categorizationService).manuallyClearItemCategory(item.getId());

        ReceiptItemUpdateRequest clearRequest = new ReceiptItemUpdateRequest();
        clearRequest.setDescription("Shampoo neu");
        clearRequest.setTotalPrice(new BigDecimal("3.99"));
        clearRequest.setCategoryId(null);

        ReceiptItemDto cleared = service.updateItem(item.getId(), clearRequest);

        assertThat(cleared.categoryId()).isNull();
        assertThat(cleared.categorySource()).isNull();
        assertThat(cleared.isManuallyEdited()).isTrue();
        verify(categorizationService).manuallyClearItemCategory(item.getId());

        ReceiptItem assignedItem = firstItem(receipt);
        assignedItem.clearCategory();
        when(receiptItemRepository.findById(item.getId())).thenReturn(Optional.of(assignedItem));
        doAnswer(invocation -> {
            assignedItem.assignCategory(drogerie, CategorySource.MANUAL);
            return null;
        }).when(categorizationService).manuallyCategorizeItem(any(), eq(drogerie.getId()));

        ReceiptItemUpdateRequest assignRequest = new ReceiptItemUpdateRequest();
        assignRequest.setDescription("Shampoo manuell");
        assignRequest.setTotalPrice(new BigDecimal("4.99"));
        assignRequest.setCategoryId(drogerie.getId());

        ReceiptItemDto assigned = service.updateItem(item.getId(), assignRequest);

        assertThat(assigned.categoryId()).isEqualTo(drogerie.getId());
        assertThat(assigned.categorySource()).isEqualTo(CategorySource.MANUAL);
        verify(categorizationService).manuallyCategorizeItem(any(), eq(drogerie.getId()));
    }

    @Test
    void updateReceiptRejectsForeignItemUpdatesAndSupportsAddingItems() {
        Receipt target = receipt(20L, 3001, "REWE", false, "Bio Milch");
        Receipt foreign = receipt(21L, 3002, "REWE", false, "Fremder Artikel");
        ReceiptItem foreignItem = firstItem(foreign);

        when(receiptRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(receiptItemRepository.findById(foreignItem.getId())).thenReturn(Optional.of(foreignItem));

        ReceiptItemUpdateRequest foreignUpdate = new ReceiptItemUpdateRequest();
        foreignUpdate.setId(foreignItem.getId());
        foreignUpdate.setDescription("Fremder Artikel neu");
        foreignUpdate.setTotalPrice(new BigDecimal("1.23"));

        assertThatThrownBy(() -> service.updateReceipt(target.getId(), new ReceiptUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(foreignUpdate))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(throwable -> assertThat(((ResponseStatusException) throwable).getStatusCode().value()).isEqualTo(400));

        AtomicLong nextItemId = new AtomicLong(5000);
        when(receiptRepository.saveAndFlush(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt saved = invocation.getArgument(0);
            saved.getItems().forEach(receiptItem -> {
                if (receiptItem.getId() == null) {
                    ReflectionTestUtils.setField(receiptItem, "id", nextItemId.incrementAndGet());
                }
            });
            return saved;
        });

        ReceiptItemCreateRequest addWithoutCategory = new ReceiptItemCreateRequest(
                null,
                "Neue Position",
                new BigDecimal("1.000"),
                "Stk",
                new BigDecimal("1.99"),
                new BigDecimal("1.99"),
                null,
                null,
                null);
        ReceiptItemDto created = service.addItem(target.getId(), addWithoutCategory);

        assertThat(created.description()).isEqualTo("Neue Position");
        assertThat(created.categoryId()).isNull();
        verify(categorizationService, never()).manuallyCategorizeItem(anyLong(), anyLong());
    }

    @Test
    void addItemWithCategoryDelegatesToManualCategorization() {
        Receipt receipt = receipt(30L, 4001, "REWE", false, "Bio Milch");
        Category drogerie = category(44L, "Drogerie");

        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        AtomicLong nextItemId = new AtomicLong(6000);
        when(receiptRepository.saveAndFlush(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt saved = invocation.getArgument(0);
            saved.getItems().forEach(receiptItem -> {
                if (receiptItem.getId() == null) {
                    ReflectionTestUtils.setField(receiptItem, "id", nextItemId.incrementAndGet());
                }
            });
            return saved;
        });
        doAnswer(invocation -> {
            ReceiptItem item = receipt.getItems().getLast();
            item.assignCategory(drogerie, CategorySource.MANUAL);
            return null;
        }).when(categorizationService).manuallyCategorizeItem(anyLong(), eq(drogerie.getId()));

        ReceiptItemCreateRequest createRequest = new ReceiptItemCreateRequest(
                2,
                "Shampoo",
                new BigDecimal("1.000"),
                "Stk",
                new BigDecimal("2.99"),
                new BigDecimal("2.99"),
                null,
                drogerie.getId(),
                null);

        ReceiptItemDto created = service.addItem(receipt.getId(), createRequest);

        assertThat(created.categoryId()).isEqualTo(drogerie.getId());
        assertThat(created.categorySource()).isEqualTo(CategorySource.MANUAL);
        verify(categorizationService).manuallyCategorizeItem(anyLong(), eq(drogerie.getId()));
    }

    @Test
    void reparseReceiptRejectsManualEditsUnlessOverwriteAndThenRecategorizes() {
        Receipt receipt = receipt(40L, 5001, "REWE", false, "Manuell geaenderter Bon");
        ReceiptItem item = firstItem(receipt);
        item.updateManualValues(null, null, null, null, null, null, null);
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId())).thenReturn(List.of(item));

        assertThatThrownBy(() -> service.reparseReceipt(receipt.getId(), false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(throwable -> assertThat(((ResponseStatusException) throwable).getStatusCode().value()).isEqualTo(409));

        ReceiptParseResult parseResult = new ReceiptParseResult(
                ParseStatus.PARSED,
                new ParsedReceipt(
                        LocalDate.of(2026, 5, 15),
                        LocalTime.of(14, 48),
                        "REWE",
                        "Am Markt 1",
                        new BigDecimal("9.99"),
                        "EUR",
                        null,
                        null,
                        null,
                        List.of()),
                null);
        when(receiptParserService.parse(anyString())).thenReturn(parseResult);
        when(receiptRepository.saveAndFlush(any(Receipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReceiptItem cleanItem = new ReceiptItem(0, "Bio Milch", new BigDecimal("9.99"));
        receipt.clearItems();
        receipt.addItem(cleanItem);
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId())).thenReturn(List.of(cleanItem));

        ReceiptDto dto = service.reparseReceipt(receipt.getId(), true);

        assertThat(dto.storeBranch()).isEqualTo("Am Markt 1");
        verify(receiptParserService).parse(receipt.getRawText());
        verify(categorizationService).categorizeReceipt(receipt.getId());
    }

    @Test
    void deleteReceiptMarksReceiptAsDeleted() {
        Receipt receipt = receipt(50L, 6001, "REWE", false, "Bio Milch");
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        service.deleteReceipt(receipt.getId());

        assertThat(receipt.getDeletedAt()).isNotNull();
        assertThat(receipt.getDeleteReason()).isEqualTo(DeleteReason.USER_DELETED);
    }

    private Receipt receipt(long id, int paperlessDocumentId, String storeName, boolean deleted, String description) {
        Receipt receipt = new Receipt(paperlessDocumentId, "raw");
        ReflectionTestUtils.setField(receipt, "id", id);
        receipt.setStoreName(storeName);
        ReceiptItem item = new ReceiptItem(0, description, new BigDecimal("1.99"));
        ReflectionTestUtils.setField(item, "id", id * 10);
        receipt.addItem(item);
        if (deleted) {
            receipt.markDeleted(DeleteReason.USER_DELETED);
        }
        return receipt;
    }

    private ReceiptItem firstItem(Receipt receipt) {
        return receipt.getItems().getFirst();
    }

    private Category category(long id, String name) {
        Category category = new Category(name, "#123456", "tag", 10);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
