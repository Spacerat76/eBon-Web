package de.ebon.api.service;

import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.DataMaintenanceResultDto;
import de.ebon.api.dto.PaperlessRawTextStatus;
import de.ebon.api.dto.PaperlessRawTextStatusDto;
import de.ebon.api.dto.RawTextSource;
import de.ebon.api.dto.ReceiptDto;
import de.ebon.api.dto.ReceiptItemCreateRequest;
import de.ebon.api.dto.ReceiptItemDto;
import de.ebon.api.dto.ReceiptItemUpdateRequest;
import de.ebon.api.dto.ReceiptUpdateRequest;
import de.ebon.categorization.CategorizationService;
import de.ebon.config.PaperlessProperties;
import de.ebon.paperless.PaperlessClient;
import de.ebon.paperless.PaperlessClientException;
import de.ebon.paperless.PaperlessDocument;
import de.ebon.parser.ParsedReceipt;
import de.ebon.parser.ParseExecutionOptions;
import de.ebon.parser.ReceiptParseApplier;
import de.ebon.parser.ReceiptParseResult;
import de.ebon.parser.ReceiptParserService;
import de.ebon.product.ProductAssignmentService;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.CategorySource;
import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.ProductAssignmentSource;
import de.ebon.persistence.model.ProductAssignmentStatus;
import de.ebon.persistence.model.ProductFamily;
import de.ebon.persistence.model.ProductVariant;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.repository.AiCategorizationLogRepository;
import de.ebon.persistence.repository.AiParsingLogRepository;
import de.ebon.persistence.repository.AppSettingRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import de.ebon.persistence.repository.ParseRuleSuggestionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
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
    private AiParsingLogRepository aiParsingLogRepository;

    @Mock
    private ParseRuleSuggestionRepository parseRuleSuggestionRepository;

    @Mock
    private AppSettingRepository appSettingRepository;

    @Mock
    private CategorizationService categorizationService;

    @Mock
    private ProductAssignmentService productAssignmentService;

    @Mock
    private ReceiptParserService receiptParserService;

    @Mock
    private ReceiptParseApplier receiptParseApplier;

    @Mock
    private PaperlessClient paperlessClient;

    private final ReceiptParseApplier realReceiptParseApplier = new ReceiptParseApplier();
    private final PaperlessProperties paperlessProperties = new PaperlessProperties();

    private ReceiptApiService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptApiService(
                receiptRepository,
                receiptItemRepository,
                categoryRepository,
                aiCategorizationLogRepository,
                appSettingRepository,
                paperlessProperties,
                categorizationService,
                receiptParserService,
                realReceiptParseApplier,
                paperlessClient);
        paperlessProperties.setPublicBaseUrl("http://paperless.web");
        paperlessProperties.setDocumentUrlTemplate("");
        lenient().when(appSettingRepository.findById(anyString())).thenReturn(Optional.empty());
    }

    // Verifies defensive pagination defaults so oversized or invalid list requests stay predictable for the UI.
    @Test
    void listReceiptsFallsBackToDefaultSortAndClampsPageSize() {
        Receipt receipt = receipt(1L, 1001, "REWE", false, "Bio Milch");
        when(receiptRepository.findAll(anyReceiptSpecification(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(receipt)));
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId()))
                .thenReturn(List.of(firstItem(receipt)));
        when(aiCategorizationLogRepository.findLatestRejectedSuggestion(eq(firstItem(receipt).getId()), any(Pageable.class)))
                .thenReturn(List.of());

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
        verify(receiptRepository).findAll(anyReceiptSpecification(), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertThat(result.sortBy()).isEqualTo("receiptDate");
        assertThat(result.sortDir()).isEqualTo("desc");
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("receiptDate").getDirection().name()).isEqualTo("DESC");
        assertThat(pageable.getSort().getOrderFor("receiptTime").getDirection().name()).isEqualTo("DESC");
        assertThat(pageable.getSort().getOrderFor("importedAt").getDirection().name()).isEqualTo("DESC");
        assertThat(pageable.getSort().getOrderFor("id").getDirection().name()).isEqualTo("DESC");
    }

    // Verifies receipt DTOs expose a safe Paperless web link built from the configured public URL.
    @Test
    void getReceiptIncludesPaperlessDocumentUrlWithoutSecrets() {
        Receipt receipt = receipt(2L, 1234, "REWE", false, "Bio Milch");
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId()))
                .thenReturn(List.of(firstItem(receipt)));
        when(aiCategorizationLogRepository.findLatestRejectedSuggestion(eq(firstItem(receipt).getId()), any(Pageable.class)))
                .thenReturn(List.of());

        ReceiptDto dto = service.getReceipt(receipt.getId());

        assertThat(dto.paperlessDocumentUrl()).isEqualTo("http://paperless.web/documents/1234/details");
        assertThat(dto.paperlessDocumentUrl()).doesNotContain("token", "secret");
    }

    // Verifies product unit prices normalize milliliters to liters instead of exposing a price per milliliter.
    @Test
    void itemDtoComputesComparablePricePerLiterForMilliliterVariant() {
        ProductFamily family = new ProductFamily("Mineralwasser", null);
        ProductVariant variant = new ProductVariant(
                family,
                "Mineralwasser 500 ml",
                new BigDecimal("500"),
                "ml",
                1,
                null,
                new BigDecimal("500"),
                "ml",
                null);
        Receipt receipt = receipt(3L, 1235, "REWE", false, "Mineralwasser 500 ml");
        ReceiptItem item = firstItem(receipt);
        item.updateManualValues(null, null, null, null, null, new BigDecimal("2.00"), null);
        item.assignProduct(family, variant, ProductAssignmentSource.RULE, ProductAssignmentStatus.AUTO_ASSIGNED, null);

        ReceiptItemDto dto = service.toItemDto(item);

        assertThat(dto.computedUnitPrice()).isEqualByComparingTo("4.0000");
        assertThat(dto.computedUnitPriceUnit()).isEqualTo("l");
    }

    // Verifies that missing and soft-deleted receipts are hidden behind the same not-found contract.
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

    // Verifies the "Ohne Kategorie" contract and manual category assignment path used by the UI.
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

    // Verifies that receipt updates cannot mutate foreign items and that new uncategorized items remain explicit.
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

    // Verifies full-object receipt edits: positions omitted from the request are deleted instead of lingering.
    @Test
    void updateReceiptDeletesOmittedExistingItems() {
        Receipt receipt = receipt(25L, 3501, "REWE", false, "Bio Milch");
        ReceiptItem retainedItem = firstItem(receipt);
        ReceiptItem removedItem = new ReceiptItem(1, "Alte Position", new BigDecimal("0.99"));
        ReflectionTestUtils.setField(removedItem, "id", 251L);
        receipt.addItem(removedItem);

        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId()))
                .thenReturn(List.of(retainedItem, removedItem), List.of(retainedItem));
        when(receiptItemRepository.findById(retainedItem.getId())).thenReturn(Optional.of(retainedItem));
        when(aiCategorizationLogRepository.findLatestRejectedSuggestion(eq(retainedItem.getId()), any(Pageable.class)))
                .thenReturn(List.of());

        ReceiptItemUpdateRequest retainedUpdate = new ReceiptItemUpdateRequest();
        retainedUpdate.setId(retainedItem.getId());
        retainedUpdate.setPositionIndex(0);
        retainedUpdate.setDescription("Bio Milch aktualisiert");
        retainedUpdate.setTotalPrice(new BigDecimal("2.49"));

        ReceiptDto updated = service.updateReceipt(receipt.getId(), new ReceiptUpdateRequest(
                receipt.getReceiptDate(),
                receipt.getReceiptTime(),
                receipt.getStoreName(),
                receipt.getStoreBranch(),
                receipt.getTotalAmount(),
                receipt.getCurrency(),
                receipt.getBonusBalance(),
                receipt.getBonusPoints(),
                receipt.getBonusType(),
                List.of(retainedUpdate)));

        assertThat(updated.items()).hasSize(1);
        assertThat(updated.items().getFirst().description()).isEqualTo("Bio Milch aktualisiert");
        verify(receiptItemRepository).delete(removedItem);
    }

    // Verifies that newly added items with a category go through the manual override service, not direct field edits.
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

    // Verifies reparse safety: manual edits block accidental overwrite unless the caller explicitly allows it.
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
        when(receiptParserService.parse(eq(receipt), any(ParseExecutionOptions.class))).thenReturn(parseResult);
        when(receiptRepository.saveAndFlush(any(Receipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReceiptItem cleanItem = new ReceiptItem(0, "Bio Milch", new BigDecimal("9.99"));
        receipt.clearItems();
        receipt.addItem(cleanItem);
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId())).thenReturn(List.of(cleanItem));

        ReceiptDto dto = serviceWithProductAssignment().reparseReceipt(receipt.getId(), true);

        assertThat(dto.storeBranch()).isEqualTo("Am Markt 1");
        verify(receiptParserService).parse(eq(receipt), any(ParseExecutionOptions.class));
        verify(categorizationService).categorizeReceipt(receipt.getId());
        verify(productAssignmentService).assignReceipt(receipt.getId());
    }

    // Product decisions are manual work even though the item-edit flag remains false.
    @ParameterizedTest
    @EnumSource(value = ProductAssignmentStatus.class, names = {"REJECTED", "NO_PRODUCT"})
    void singleReparseRequiresConsentToReplaceExplicitProductDecision(ProductAssignmentStatus decision) {
        Receipt receipt = receipt(41L, 5002, "REWE", false, "Bio Milch");
        ReceiptItem original = firstItem(receipt);
        markDecision(original, decision);
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        stubReparse(receipt);

        assertThatThrownBy(() -> service.reparseReceipt(receipt.getId(), false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409));
        assertThat(firstItem(receipt)).isSameAs(original);
        assertThat(original.getProductAssignmentStatus()).isEqualTo(decision);
        verify(receiptParserService, never()).parse(any(Receipt.class), any(ParseExecutionOptions.class));

        service.reparseReceipt(receipt.getId(), true);

        assertThat(firstItem(receipt)).isNotSameAs(original);
        assertThat(firstItem(receipt).getProductAssignmentStatus()).isNull();
        verify(receiptParserService).parse(eq(receipt), any(ParseExecutionOptions.class));
        verify(categorizationService).categorizeReceipt(receipt.getId());
    }

    @ParameterizedTest
    @EnumSource(value = ProductAssignmentStatus.class, names = {"REJECTED", "NO_PRODUCT"})
    void bulkReparseRequiresConsentToReplaceExplicitProductDecision(ProductAssignmentStatus decision) {
        Receipt receipt = receipt(42L, 5003, "REWE", false, "Bio Milch");
        ReceiptItem original = firstItem(receipt);
        markDecision(original, decision);
        when(receiptRepository.findByDeletedAtIsNullOrderByImportedAtDesc()).thenReturn(List.of(receipt));
        stubReparse(receipt);

        DataMaintenanceResultDto protectedResult = service.reparseAllReceipts(false);
        assertThat(protectedResult.skippedManualReceipts()).isEqualTo(1);
        assertThat(protectedResult.processedReceipts()).isZero();
        assertThat(firstItem(receipt)).isSameAs(original);
        assertThat(original.getProductAssignmentStatus()).isEqualTo(decision);
        verify(receiptParserService, never()).parse(any(Receipt.class), any(ParseExecutionOptions.class));

        DataMaintenanceResultDto overwritten = service.reparseAllReceipts(true);

        assertThat(overwritten.skippedManualReceipts()).isZero();
        assertThat(overwritten.processedReceipts()).isEqualTo(1);
        assertThat(firstItem(receipt)).isNotSameAs(original);
        assertThat(firstItem(receipt).getProductAssignmentStatus()).isNull();
        verify(receiptParserService).parse(eq(receipt), any(ParseExecutionOptions.class));
        verify(categorizationService).categorizeReceipt(receipt.getId());
    }

    private void markDecision(ReceiptItem item, ProductAssignmentStatus decision) {
        if (decision == ProductAssignmentStatus.REJECTED) {
            item.markProductRejected();
        } else {
            item.markNoProduct();
        }
        assertThat(item.isManuallyEdited()).isFalse();
    }

    private void stubReparse(Receipt receipt) {
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId()))
                .thenAnswer(invocation -> receipt.getItems());
        when(receiptRepository.saveAndFlush(any(Receipt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ReceiptParseResult parsed = new ReceiptParseResult(ParseStatus.PARSED,
                new ParsedReceipt(LocalDate.of(2026, 6, 18), null, "REWE", null,
                        new BigDecimal("1.99"), "EUR", null, null, null,
                        List.of(new de.ebon.parser.ParsedReceiptItem(0, "Bio Milch", BigDecimal.ONE,
                                "Stk", new BigDecimal("1.99"), new BigDecimal("1.99"), null))), null);
        when(receiptParserService.parse(eq(receipt), any(ParseExecutionOptions.class))).thenReturn(parsed);
    }

    // Verifies the status check ignores transport-only line-ending differences and exposes no raw text.
    @Test
    void paperlessRawTextStatusTreatsEquivalentLineEndingsAsUnchanged() {
        Receipt receipt = receipt(70L, 8001, "REWE", false, "Bio Milch");
        ReflectionTestUtils.setField(receipt, "rawText", "REWE\r\nBio Milch\r\nSUMME 1,99");
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(paperlessClient.fetchDocumentById(8001))
                .thenReturn(new PaperlessDocument(8001, "REWE", "2026-06-19", "REWE\nBio Milch\nSUMME 1,99"));

        PaperlessRawTextStatusDto result = service.paperlessRawTextStatus(receipt.getId());

        assertThat(result.status()).isEqualTo(PaperlessRawTextStatus.UNCHANGED);
    }

    // Verifies the status check reports an actual Paperless content change without returning that content.
    @Test
    void paperlessRawTextStatusReportsChangedForDifferentContent() {
        Receipt receipt = receipt(73L, 8004, "REWE", false, "Bio Milch");
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(paperlessClient.fetchDocumentById(8004))
                .thenReturn(new PaperlessDocument(8004, "REWE", "2026-06-19", "Korrigierter Rohtext"));

        PaperlessRawTextStatusDto result = service.paperlessRawTextStatus(receipt.getId());

        assertThat(result.status()).isEqualTo(PaperlessRawTextStatus.CHANGED);
    }

    // Verifies that a confirmed Paperless source replaces stored raw text before the parser sees the receipt.
    @Test
    void reparseReceiptUsesConfirmedPaperlessRawTextTransactionally() {
        Receipt receipt = receipt(71L, 8002, "REWE", false, "Alte Position");
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId()))
                .thenReturn(List.of(firstItem(receipt)));
        when(paperlessClient.fetchDocumentById(8002))
                .thenReturn(new PaperlessDocument(8002, "REWE", "2026-06-19", "Neuer Rohtext"));
        when(receiptParserService.parse(eq(receipt), any(ParseExecutionOptions.class))).thenAnswer(invocation -> {
            assertThat(receipt.getRawText()).isEqualTo("Neuer Rohtext");
            return new ReceiptParseResult(ParseStatus.PARSE_ERROR, null, "Test-Parsefehler");
        });
        when(receiptRepository.saveAndFlush(any(Receipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reparseReceipt(receipt.getId(), false, true, null, false, RawTextSource.PAPERLESS);

        assertThat(receipt.getRawText()).isEqualTo("Neuer Rohtext");
        verify(paperlessClient).fetchDocumentById(8002);
    }

    // Verifies that an unavailable Paperless instance is represented as a safe status without leaking client details.
    @Test
    void paperlessRawTextStatusReportsUnavailableWhenPaperlessCannotBeRead() {
        Receipt receipt = receipt(72L, 8003, "REWE", false, "Bio Milch");
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(paperlessClient.fetchDocumentById(8003))
                .thenThrow(new PaperlessClientException("internal Paperless transport detail"));

        PaperlessRawTextStatusDto result = service.paperlessRawTextStatus(receipt.getId());

        assertThat(result.status()).isEqualTo(PaperlessRawTextStatus.UNAVAILABLE);
    }

    // Verifies a failed confirmed refresh does not replace the persisted raw text or invoke the parser.
    @Test
    void reparseReceiptKeepsStoredRawTextWhenPaperlessRefreshFails() {
        Receipt receipt = receipt(74L, 8005, "REWE", false, "Bio Milch");
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId()))
                .thenReturn(List.of(firstItem(receipt)));
        when(paperlessClient.fetchDocumentById(8005))
                .thenThrow(new PaperlessClientException("internal Paperless transport detail"));

        assertThatThrownBy(() -> service.reparseReceipt(
                        receipt.getId(), false, true, null, false, RawTextSource.PAPERLESS))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(throwable -> assertThat(((ResponseStatusException) throwable).getStatusCode().value())
                        .isEqualTo(502));

        assertThat(receipt.getRawText()).isEqualTo("raw");
        verify(receiptParserService, never()).parse(any(Receipt.class), any(ParseExecutionOptions.class));
    }

    // Verifies the administrative reparse-all action preserves manual work by default and reports skipped receipts.
    @Test
    void reparseAllReceiptsSkipsManualReceiptsUnlessOverwriteIsRequested() {
        Receipt cleanReceipt = receipt(60L, 7001, "REWE", false, "Bio Milch");
        Receipt manualReceipt = receipt(61L, 7002, "DM", false, "Manuell geaendert");
        ReceiptItem manualItem = firstItem(manualReceipt);
        manualItem.updateManualValues(null, null, null, null, null, null, null);
        ReceiptParseResult parseResult = new ReceiptParseResult(
                ParseStatus.PARSED,
                new ParsedReceipt(
                        LocalDate.of(2026, 6, 1),
                        LocalTime.of(10, 15),
                        "REWE",
                        "Am Markt 1",
                        new BigDecimal("1.99"),
                        "EUR",
                        null,
                        null,
                        null,
                        List.of()),
                null);

        when(receiptRepository.findByDeletedAtIsNullOrderByImportedAtDesc())
                .thenReturn(List.of(cleanReceipt, manualReceipt));
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(cleanReceipt.getId()))
                .thenReturn(List.of(firstItem(cleanReceipt)));
        when(receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(manualReceipt.getId()))
                .thenReturn(List.of(manualItem));
        when(receiptParserService.parse(eq(cleanReceipt), any(ParseExecutionOptions.class))).thenReturn(parseResult);
        when(receiptRepository.saveAndFlush(any(Receipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DataMaintenanceResultDto result = service.reparseAllReceipts(false);

        assertThat(result.totalReceipts()).isEqualTo(2);
        assertThat(result.processedReceipts()).isEqualTo(1);
        assertThat(result.skippedManualReceipts()).isEqualTo(1);
        verify(categorizationService).categorizeReceipt(cleanReceipt.getId());
        verify(categorizationService, never()).categorizeReceipt(manualReceipt.getId());
    }

    // Verifies user deletes are soft deletes so imported receipt data is not physically lost.
    @Test
    void deleteReceiptMarksReceiptAsDeleted() {
        Receipt receipt = receipt(50L, 6001, "REWE", false, "Bio Milch");
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        service.deleteReceipt(receipt.getId());

        assertThat(receipt.getDeletedAt()).isNotNull();
        assertThat(receipt.getDeleteReason()).isEqualTo(DeleteReason.USER_DELETED);
    }

    private ReceiptApiService serviceWithProductAssignment() {
        return new ReceiptApiService(
                receiptRepository,
                receiptItemRepository,
                categoryRepository,
                aiCategorizationLogRepository,
                aiParsingLogRepository,
                parseRuleSuggestionRepository,
                appSettingRepository,
                paperlessProperties,
                categorizationService,
                productAssignmentService,
                receiptParserService,
                realReceiptParseApplier,
                paperlessClient);
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

    private Specification<Receipt> anyReceiptSpecification() {
        return org.mockito.ArgumentMatchers.<Specification<Receipt>>any();
    }
}
