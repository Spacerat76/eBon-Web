package de.ebon.api.service;

import de.ebon.api.dto.AiSuggestionDto;
import de.ebon.api.dto.AiParsingSummaryDto;
import de.ebon.api.dto.PageResponse;
import de.ebon.api.dto.DataMaintenanceResultDto;
import de.ebon.api.dto.PaperlessRawTextStatus;
import de.ebon.api.dto.PaperlessRawTextStatusDto;
import de.ebon.api.dto.ParseTraceLineDto;
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
import de.ebon.parser.ReceiptParseApplier;
import de.ebon.parser.ReceiptParseResult;
import de.ebon.parser.ReceiptParserService;
import de.ebon.product.ProductAssignmentService;
import de.ebon.product.ProductAssignmentTransferService;
import de.ebon.product.ProductPriceCalculator;
import de.ebon.parser.AiParsingTextMode;
import de.ebon.parser.ParseExecutionOptions;
import de.ebon.persistence.model.AiCategorizationLog;
import de.ebon.persistence.model.AiParsingLog;
import de.ebon.persistence.model.Category;
import de.ebon.persistence.model.DeleteReason;
import de.ebon.persistence.model.ParseRuleSuggestionStatus;
import de.ebon.persistence.model.ParseStatus;
import de.ebon.persistence.model.ParseLineType;
import de.ebon.persistence.model.Receipt;
import de.ebon.persistence.model.ReceiptItem;
import de.ebon.persistence.model.ReceiptParseTrace;
import de.ebon.persistence.repository.AiCategorizationLogRepository;
import de.ebon.persistence.repository.AiParsingLogRepository;
import de.ebon.persistence.repository.AppSettingRepository;
import de.ebon.persistence.repository.CategoryRepository;
import de.ebon.persistence.repository.ParseRuleSuggestionRepository;
import de.ebon.persistence.repository.ReceiptItemRepository;
import de.ebon.persistence.repository.ReceiptParseTraceRepository;
import de.ebon.persistence.repository.ReceiptRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReceiptApiService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReceiptRepository receiptRepository;
    private final ReceiptItemRepository receiptItemRepository;
    private final ReceiptParseTraceRepository receiptParseTraceRepository;
    private final CategoryRepository categoryRepository;
    private final AiCategorizationLogRepository aiCategorizationLogRepository;
    private final AiParsingLogRepository aiParsingLogRepository;
    private final ParseRuleSuggestionRepository parseRuleSuggestionRepository;
    private final AppSettingRepository appSettingRepository;
    private final PaperlessProperties paperlessProperties;
    private final CategorizationService categorizationService;
    private final ProductAssignmentService productAssignmentService;
    private final ProductAssignmentTransferService productAssignmentTransferService;
    private final ReceiptParserService receiptParserService;
    private final ReceiptParseApplier receiptParseApplier;
    private final PaperlessClient paperlessClient;

    public ReceiptApiService(
            ReceiptRepository receiptRepository,
            ReceiptItemRepository receiptItemRepository,
            CategoryRepository categoryRepository,
            AiCategorizationLogRepository aiCategorizationLogRepository,
            AppSettingRepository appSettingRepository,
            PaperlessProperties paperlessProperties,
            CategorizationService categorizationService,
            ReceiptParserService receiptParserService,
            ReceiptParseApplier receiptParseApplier) {
        this(
                receiptRepository,
                receiptItemRepository,
                null,
                categoryRepository,
                aiCategorizationLogRepository,
                null,
                null,
                appSettingRepository,
                paperlessProperties,
                categorizationService,
                null,
                null,
                receiptParserService,
                receiptParseApplier,
                null);
    }

    public ReceiptApiService(
            ReceiptRepository receiptRepository,
            ReceiptItemRepository receiptItemRepository,
            CategoryRepository categoryRepository,
            AiCategorizationLogRepository aiCategorizationLogRepository,
            AppSettingRepository appSettingRepository,
            PaperlessProperties paperlessProperties,
            CategorizationService categorizationService,
            ReceiptParserService receiptParserService,
            ReceiptParseApplier receiptParseApplier,
            PaperlessClient paperlessClient) {
        this(
                receiptRepository,
                receiptItemRepository,
                null,
                categoryRepository,
                aiCategorizationLogRepository,
                null,
                null,
                appSettingRepository,
                paperlessProperties,
                categorizationService,
                null,
                null,
                receiptParserService,
                receiptParseApplier,
                paperlessClient);
    }

    public ReceiptApiService(
            ReceiptRepository receiptRepository,
            ReceiptItemRepository receiptItemRepository,
            CategoryRepository categoryRepository,
            AiCategorizationLogRepository aiCategorizationLogRepository,
            AiParsingLogRepository aiParsingLogRepository,
            ParseRuleSuggestionRepository parseRuleSuggestionRepository,
            AppSettingRepository appSettingRepository,
            PaperlessProperties paperlessProperties,
            CategorizationService categorizationService,
            ProductAssignmentService productAssignmentService,
            ReceiptParserService receiptParserService,
            ReceiptParseApplier receiptParseApplier,
            PaperlessClient paperlessClient) {
        this(
                receiptRepository,
                receiptItemRepository,
                null,
                categoryRepository,
                aiCategorizationLogRepository,
                aiParsingLogRepository,
                parseRuleSuggestionRepository,
                appSettingRepository,
                paperlessProperties,
                categorizationService,
                productAssignmentService,
                null,
                receiptParserService,
                receiptParseApplier,
                paperlessClient);
    }

    @Autowired
    public ReceiptApiService(
            ReceiptRepository receiptRepository,
            ReceiptItemRepository receiptItemRepository,
            ReceiptParseTraceRepository receiptParseTraceRepository,
            CategoryRepository categoryRepository,
            AiCategorizationLogRepository aiCategorizationLogRepository,
            AiParsingLogRepository aiParsingLogRepository,
            ParseRuleSuggestionRepository parseRuleSuggestionRepository,
            AppSettingRepository appSettingRepository,
            PaperlessProperties paperlessProperties,
            CategorizationService categorizationService,
            ProductAssignmentService productAssignmentService,
            ProductAssignmentTransferService productAssignmentTransferService,
            ReceiptParserService receiptParserService,
            ReceiptParseApplier receiptParseApplier,
            PaperlessClient paperlessClient) {
        this.receiptRepository = receiptRepository;
        this.receiptItemRepository = receiptItemRepository;
        this.receiptParseTraceRepository = receiptParseTraceRepository;
        this.categoryRepository = categoryRepository;
        this.aiCategorizationLogRepository = aiCategorizationLogRepository;
        this.aiParsingLogRepository = aiParsingLogRepository;
        this.parseRuleSuggestionRepository = parseRuleSuggestionRepository;
        this.appSettingRepository = appSettingRepository;
        this.paperlessProperties = paperlessProperties;
        this.categorizationService = categorizationService;
        this.productAssignmentService = productAssignmentService;
        this.productAssignmentTransferService = productAssignmentTransferService;
        this.receiptParserService = receiptParserService;
        this.receiptParseApplier = receiptParseApplier;
        this.paperlessClient = paperlessClient;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceiptDto> listReceipts(
            int page,
            int size,
            String sortBy,
            String sortDir,
            ParseStatus status,
            LocalDate dateFrom,
            LocalDate dateTo,
            String store,
            boolean includeDeleted) {
        String safeSortBy = safeReceiptSort(sortBy);
        String safeSortDir = safeSortDirection(sortDir);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                receiptSort(safeSortBy, safeSortDir));
        return PageResponse.from(receiptRepository.findAll(
                receiptSpecification(status, dateFrom, dateTo, store, includeDeleted),
                pageable).map(receipt -> toReceiptDto(
                        receipt,
                        receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId()),
                        false)), safeSortBy, safeSortDir);
    }

    @Transactional(readOnly = true)
    public ReceiptDto getReceipt(Long id) {
        Receipt receipt = activeReceipt(id);
        return toReceiptDto(receipt, receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(id), true);
    }

    @Transactional(readOnly = true)
    public List<ParseTraceLineDto> parseTrace(Long id) {
        Receipt receipt = activeReceipt(id);
        return receiptParseTraceRepository.findByReceipt_IdOrderByLineNumberAsc(receipt.getId()).stream()
                .map(trace -> toParseTraceLineDto(receipt, trace))
                .toList();
    }

    @Transactional
    public ReceiptDto updateReceipt(Long id, ReceiptUpdateRequest request) {
        Receipt receipt = activeReceipt(id);
        receipt.updateManualValues(
                request.receiptDate(),
                request.receiptTime(),
                request.storeName(),
                request.storeBranch(),
                request.totalAmount(),
                request.currency(),
                request.bonusBalance(),
                request.bonusPoints(),
                request.bonusType());

        if (request.items() != null) {
            List<ReceiptItem> existingItems = receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId());
            Set<Long> retainedItemIds = new HashSet<>();
            for (ReceiptItemUpdateRequest itemRequest : request.items()) {
                if (itemRequest.getId() == null) {
                    addItem(receipt, toCreateRequest(itemRequest));
                } else {
                    retainedItemIds.add(itemRequest.getId());
                    updateItemOnReceipt(receipt, itemRequest.getId(), itemRequest);
                }
            }
            existingItems.stream()
                    .filter(item -> !retainedItemIds.contains(item.getId()))
                    .forEach(receiptItemRepository::delete);
        }

        receiptRepository.flush();
        return getReceipt(id);
    }

    @Transactional
    public ReceiptItemDto updateItem(Long id, ReceiptItemUpdateRequest request) {
        ReceiptItem item = receiptItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bon-Position nicht gefunden."));
        updateItemValues(item, request);
        if (request.isCategoryIdProvided()) {
            applyManualCategoryChange(item.getId(), request.getCategoryId());
        }
        item.getReceipt().markManuallyEdited();
        receiptItemRepository.flush();
        return toItemDto(receiptItemRepository.findById(id).orElseThrow());
    }

    @Transactional
    public ReceiptItemDto addItem(Long receiptId, ReceiptItemCreateRequest request) {
        Receipt receipt = activeReceipt(receiptId);
        ReceiptItem item = addItem(receipt, request);
        receiptRepository.saveAndFlush(receipt);
        if (request.categoryId() != null) {
            categorizationService.manuallyCategorizeItem(item.getId(), request.categoryId());
        }
        return toItemDto(item);
    }

    @Transactional
    public void deleteItem(Long id) {
        ReceiptItem item = receiptItemRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bon-Position nicht gefunden."));
        item.getReceipt().markManuallyEdited();
        receiptItemRepository.delete(item);
    }

    @Transactional
    public ReceiptDto reparseReceipt(Long id, boolean overwriteManualEdits) {
        return reparseReceipt(id, overwriteManualEdits, true, null, false, RawTextSource.STORED);
    }

    @Transactional
    public ReceiptDto reparseReceipt(
            Long id,
            boolean overwriteManualEdits,
            boolean useAiFallback,
            AiParsingTextMode aiTextMode,
            boolean confirmFullText) {
        return reparseReceipt(id, overwriteManualEdits, useAiFallback, aiTextMode, confirmFullText, RawTextSource.STORED);
    }

    @Transactional
    public ReceiptDto reparseReceipt(
            Long id,
            boolean overwriteManualEdits,
            boolean useAiFallback,
            AiParsingTextMode aiTextMode,
            boolean confirmFullText,
            RawTextSource rawTextSource) {
        Receipt receipt = activeReceipt(id);
        if (hasManualEdits(receipt) && !overwriteManualEdits) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bon enthaelt manuell editierte Positionen. overwriteManualEdits=true ist erforderlich.");
        }

        if (rawTextSource == RawTextSource.PAPERLESS) {
            receipt.replaceRawText(currentPaperlessRawText(receipt));
        }

        reparseReceipt(receipt, ParseExecutionOptions.manual(useAiFallback, aiTextMode, confirmFullText));
        return getReceipt(id);
    }

    @Transactional(readOnly = true)
    public PaperlessRawTextStatusDto paperlessRawTextStatus(Long id) {
        Receipt receipt = activeReceipt(id);
        if (paperlessClient == null) {
            return new PaperlessRawTextStatusDto(PaperlessRawTextStatus.UNAVAILABLE);
        }
        try {
            String currentRawText = currentPaperlessRawText(receipt);
            PaperlessRawTextStatus status = rawTextsEquivalent(receipt.getRawText(), currentRawText)
                    ? PaperlessRawTextStatus.UNCHANGED
                    : PaperlessRawTextStatus.CHANGED;
            return new PaperlessRawTextStatusDto(status);
        } catch (ResponseStatusException exception) {
            return new PaperlessRawTextStatusDto(PaperlessRawTextStatus.UNAVAILABLE);
        }
    }

    @Transactional
    public DataMaintenanceResultDto reparseAllReceipts(boolean overwriteManualEdits) {
        List<Receipt> receipts = receiptRepository.findByDeletedAtIsNullOrderByImportedAtDesc();
        long skippedManualReceipts = 0;
        long processedReceipts = 0;

        for (Receipt receipt : receipts) {
            if (!overwriteManualEdits && hasManualEdits(receipt)) {
                skippedManualReceipts++;
                continue;
            }
            reparseReceipt(receipt, ParseExecutionOptions.bulk());
            processedReceipts++;
        }

        return new DataMaintenanceResultDto(
                "Bons wurden erneut geparst.",
                receipts.size(),
                processedReceipts,
                skippedManualReceipts,
                0,
                0);
    }

    private void reparseReceipt(Receipt receipt, ParseExecutionOptions options) {
        List<ReceiptItem> previousItems = new ArrayList<>(
                receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId()));
        ReceiptParseResult parseResult = receiptParserService.parse(receipt, options);
        receipt.clearItems();
        receiptRepository.saveAndFlush(receipt);
        receiptParseApplier.apply(receipt, parseResult);
        if (productAssignmentTransferService != null) {
            productAssignmentTransferService.transferConfirmedAssignments(previousItems, receipt.getItems());
        }
        Receipt savedReceipt = receiptRepository.saveAndFlush(receipt);
        categorizationService.categorizeReceipt(savedReceipt.getId());
        if (productAssignmentService != null) {
            productAssignmentService.assignReceipt(savedReceipt.getId());
        }
    }

    private String currentPaperlessRawText(Receipt receipt) {
        if (paperlessClient == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Paperless-Rohtext konnte nicht aktualisiert werden.");
        }
        try {
            PaperlessDocument document = paperlessClient.fetchDocumentById(receipt.getPaperlessDocumentId());
            if (document.content() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Paperless-Rohtext konnte nicht aktualisiert werden.");
            }
            return document.content();
        } catch (PaperlessClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Paperless-Rohtext konnte nicht aktualisiert werden.");
        }
    }

    private boolean rawTextsEquivalent(String storedRawText, String currentRawText) {
        return normalizeLineEndings(storedRawText).equals(normalizeLineEndings(currentRawText));
    }

    private String normalizeLineEndings(String rawText) {
        return rawText == null ? "" : rawText.replace("\r\n", "\n").replace('\r', '\n');
    }

    @Transactional
    public void deleteReceipt(Long id) {
        Receipt receipt = activeReceipt(id);
        receipt.markDeleted(DeleteReason.USER_DELETED);
    }

    private Specification<Receipt> receiptSpecification(
            ParseStatus status,
            LocalDate dateFrom,
            LocalDate dateTo,
            String store,
            boolean includeDeleted) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeDeleted) {
                predicates.add(builder.isNull(root.get("deletedAt")));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("parseStatus"), status));
            }
            if (dateFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("receiptDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("receiptDate"), dateTo));
            }
            if (store != null && !store.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("storeName")),
                        "%" + store.toLowerCase(Locale.ROOT).trim() + "%"));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Receipt activeReceipt(Long id) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bon nicht gefunden."));
        if (receipt.getDeletedAt() != null) {
            throw new EntityNotFoundException("Bon nicht gefunden.");
        }
        return receipt;
    }

    private boolean hasManualEdits(Receipt receipt) {
        return receipt.getParseStatus() == ParseStatus.MANUALLY_EDITED
                || receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId()).stream()
                .anyMatch(ReceiptItem::requiresExplicitReparseOverwrite);
    }

    private void updateItemOnReceipt(Receipt receipt, Long itemId, ReceiptItemUpdateRequest request) {
        ReceiptItem item = receiptItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Bon-Position nicht gefunden."));
        if (!item.getReceipt().getId().equals(receipt.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bon-Position gehoert nicht zu diesem Bon.");
        }
        updateItemValues(item, request);
        if (request.isCategoryIdProvided()) {
            applyManualCategoryChange(item.getId(), request.getCategoryId());
        }
        receipt.markManuallyEdited();
    }

    private void updateItemValues(ReceiptItem item, ReceiptItemUpdateRequest request) {
        item.updateManualValues(
                request.getPositionIndex(),
                request.getDescription(),
                request.getQuantity(),
                request.getUnit(),
                request.getUnitPrice(),
                request.getTotalPrice(),
                request.getDiscountAmount());
    }

    private void applyManualCategoryChange(Long itemId, Long categoryId) {
        if (categoryId == null) {
            categorizationService.manuallyClearItemCategory(itemId);
        } else {
            categorizationService.manuallyCategorizeItem(itemId, categoryId);
        }
    }

    private ReceiptItem addItem(Receipt receipt, ReceiptItemCreateRequest request) {
        int positionIndex = request.positionIndex() == null
                ? receiptItemRepository.findByReceipt_IdOrderByPositionIndexAsc(receipt.getId()).size()
                : request.positionIndex();
        ReceiptItem item = new ReceiptItem(positionIndex, request.description(), request.totalPrice());
        item.updateManualValues(
                null,
                null,
                request.quantity(),
                request.unit(),
                request.unitPrice(),
                null,
                request.discountAmount());
        receipt.addItem(item);
        receipt.markManuallyEdited();
        return item;
    }

    private ReceiptItemCreateRequest toCreateRequest(ReceiptItemUpdateRequest request) {
        if (request.getDescription() == null || request.getTotalPrice() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Neue Positionen benoetigen description und totalPrice.");
        }
        return new ReceiptItemCreateRequest(
                request.getPositionIndex(),
                request.getDescription(),
                request.getQuantity(),
                request.getUnit(),
                request.getUnitPrice(),
                request.getTotalPrice(),
                request.getDiscountAmount(),
                request.getCategoryId(),
                request.getCategorySource());
    }

    public ReceiptDto toReceiptDto(Receipt receipt, List<ReceiptItem> items) {
        return toReceiptDto(receipt, items, false);
    }

    public ReceiptDto toReceiptDto(Receipt receipt, List<ReceiptItem> items, boolean includeRawText) {
        return new ReceiptDto(
                receipt.getId(),
                receipt.getPaperlessDocumentId(),
                paperlessDocumentUrl(receipt.getPaperlessDocumentId()),
                receipt.getImportedAt(),
                receipt.getReceiptDate(),
                receipt.getReceiptTime(),
                receipt.getStoreName(),
                receipt.getStoreBranch(),
                receipt.getTotalAmount(),
                receipt.getCurrency(),
                receipt.getBonusBalance(),
                receipt.getBonusPoints(),
                receipt.getBonusType(),
                receipt.getParseStatus(),
                receipt.getParseSource(),
                receipt.getParseErrorMessage(),
                receipt.getReceiptFormatProfile() == null ? null : receipt.getReceiptFormatProfile().getId(),
                receipt.getFormatProfileVersion(),
                unresolvedLineCount(receipt.getId()),
                aiParsingSummary(receipt.getId()),
                receipt.getDeletedAt(),
                receipt.getDeleteReason(),
                includeRawText ? receipt.getRawText() : null,
                items.stream().map(this::toItemDto).toList());
    }

    public ReceiptItemDto toItemDto(ReceiptItem item) {
        Category category = item.getCategory();
        AiSuggestionDto suggestion = category == null ? latestAiSuggestion(item.getId()) : null;
        ProductPriceCalculator.PriceQuote priceQuote = ProductPriceCalculator.quote(item);
        return new ReceiptItemDto(
                item.getId(),
                item.getReceipt().getId(),
                item.getPositionIndex(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnit(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getDiscountAmount(),
                item.getExtractionStatus(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                category == null ? null : item.getCategorySource(),
                item.isManuallyEdited(),
                suggestion,
                item.getProductFamily() == null ? null : item.getProductFamily().getId(),
                item.getProductFamily() == null ? null : item.getProductFamily().getName(),
                item.getProductVariant() == null ? null : item.getProductVariant().getId(),
                item.getProductVariant() == null ? null : item.getProductVariant().getName(),
                item.getProductAssignmentSource(),
                item.getProductAssignmentStatus(),
                item.getProductAssignmentConfidence(),
                priceQuote.normalizedUnitPrice(),
                priceQuote.normalizedUnit(),
                item.isExcludedFromProductPriceComparison(),
                item.getProductPriceExclusionReason());
    }

    private long unresolvedLineCount(Long receiptId) {
        return receiptParseTraceRepository == null ? 0
                : receiptParseTraceRepository.countByReceipt_IdAndLineType(receiptId, ParseLineType.UNRESOLVED);
    }

    private ParseTraceLineDto toParseTraceLineDto(Receipt receipt, ReceiptParseTrace trace) {
        return new ParseTraceLineDto(
                trace.getLineNumber(),
                receiptLine(receipt.getRawText(), trace.getLineNumber()),
                trace.getLineType(),
                trace.getPositionIndex(),
                trace.getReason(),
                trace.isNeedsReview(),
                trace.getFormatProfile() == null ? null : trace.getFormatProfile().getId(),
                trace.getFormatProfileVersion());
    }

    private String receiptLine(String rawText, int lineNumber) {
        if (rawText == null || lineNumber < 1) {
            return null;
        }
        String[] lines = rawText.split("\\R", -1);
        return lineNumber <= lines.length ? lines[lineNumber - 1] : null;
    }

    private AiSuggestionDto latestAiSuggestion(Long receiptItemId) {
        return aiCategorizationLogRepository
                .findLatestRejectedSuggestion(receiptItemId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::toAiSuggestionDto)
                .orElse(null);
    }

    private AiSuggestionDto toAiSuggestionDto(AiCategorizationLog log) {
        Category suggestedCategory = log.getSuggestedCategory();
        return new AiSuggestionDto(
                suggestedCategory == null ? null : suggestedCategory.getId(),
                suggestedCategory == null ? log.getSuggestedCategoryName() : suggestedCategory.getName(),
                log.getAiConfidence(),
                log.getRejectionReason());
    }

    private AiParsingSummaryDto aiParsingSummary(Long receiptId) {
        if (aiParsingLogRepository == null || parseRuleSuggestionRepository == null) {
            return null;
        }
        return aiParsingLogRepository.findFirstByReceipt_IdOrderByStartedAtDesc(receiptId)
                .map(log -> new AiParsingSummaryDto(
                        log.getStatus(),
                        log.getTrigger(),
                        log.getModelUsed(),
                        log.getOverallConfidence(),
                        parseRuleSuggestionRepository.countByReceipt_IdAndStatus(
                                receiptId,
                                ParseRuleSuggestionStatus.OPEN) > 0))
                .orElse(null);
    }

    private String safeReceiptSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "receiptDate";
        }
        return switch (sortBy) {
            case "receiptDate", "importedAt", "storeName", "totalAmount", "parseStatus" -> sortBy;
            default -> "receiptDate";
        };
    }

    private String safeSortDirection(String sortDir) {
        return "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
    }

    private Sort receiptSort(String sortBy, String sortDir) {
        Sort.Direction direction = Sort.Direction.fromString(sortDir);
        Sort sort = Sort.by(direction, sortBy);
        if ("receiptDate".equals(sortBy)) {
            sort = sort
                    .and(Sort.by(direction, "receiptTime"))
                    .and(Sort.by(direction, "importedAt"));
        }
        return sort.and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private String paperlessDocumentUrl(Integer paperlessDocumentId) {
        if (paperlessDocumentId == null) {
            return null;
        }

        String documentId = paperlessDocumentId.toString();
        String template = setting("paperless_document_url_template", paperlessProperties.getDocumentUrlTemplate());
        if (template != null && !template.isBlank()) {
            return template.trim().replace("{paperlessDocumentId}", documentId);
        }

        String publicBaseUrl = setting("paperless_public_base_url", paperlessProperties.getPublicBaseUrl());
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = paperlessProperties.getBaseUrl();
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }

        return publicBaseUrl.replaceAll("/+$", "") + "/documents/" + documentId + "/details";
    }

    private String setting(String key, String fallback) {
        return appSettingRepository.findById(key)
                .map(setting -> setting.getValue())
                .orElse(fallback);
    }
}
