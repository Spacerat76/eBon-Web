# Graph Report - .  (2026-07-12)

## Corpus Check
- 438 files · ~147,041 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 3473 nodes · 9802 edges · 183 communities (150 shown, 33 thin omitted)
- Extraction: 85% EXTRACTED · 15% INFERRED · 0% AMBIGUOUS · INFERRED: 1443 edges (avg confidence: 0.8)
- Token cost: unavailable (the Codex collaboration API did not expose per-agent usage; zero was intentionally not reported as actual usage)

## Community Hubs (Navigation)
- Types Module
- Products API Contract Tests
- Receipt API Service
- Category Module
- Search Page
- Products Controller
- Response Status Exception
- Product Assignment Log Repository
- Add Item
- Categorization Rule Management Service
- Receipt Module
- Categorization Rule
- Dashboard Page
- Product Review Service
- Paperless Properties
- Product Price Controller
- Rule Based Receipt Parser
- Product Price Service
- Receipt Item
- Receipts Page
- Reports Page
- Receipt API Service Cluster 21
- Get Id
- Categorization Service Tests
- AI Categorization Properties
- Receipt API Contract Tests
- Backup Service
- Receipt Item Update Request
- Parse Rule
- Settings Page
- Parse Items
- Products Page
- Query API Service
- Product Rule
- Mock API
- Backup Service Tests
- AI Categorization Log
- AI Parsing Properties
- Parse Rule Suggestion
- API Controller Web Mvc Tests
- AI Parsing Controller
- Paperless Sync Service Tests
- Receipt Parser Test Corpus
- Settings Service
- Categories Controller
- Parse Rule Validation Status
- Parse Module
- AI Parsing Log
- Categorization Service
- Product Family
- Incremental AI Implementation Workflow
- AI Parsing Trigger
- AI Parsing API Service
- Version Service
- Query API Service Tests
- Settings Connection Test Response
- Global Exception Handler
- Reports Controller
- Rolling Backup Properties
- Try Fallback
- Open Router AI Receipt Parsing
- Compiler Options
- Parse Rule Type
- Sync Controller
- Sync Log
- Product Variant
- Sync Log Entry
- App Setting
- Parse With Metadata
- Quote Module
- Rule Match Type
- Paperless Sync Service
- Smoke Mjs
- API Error Factory
- Receipt Parser Corpus Tests
- Reparse Receipt
- Components Json
- App Module
- Dev Dependencies
- Backup Controller
- Settings Controller
- Receipt Parser Properties
- Migration Seeds Store Specific Coca
- Create Automatic Backup
- Once Per Request Filter
- Backend Skeleton Security Tests
- AI Parsing Fallback Service Tests
- Search Controller
- Receipt Items Controller
- Open Router AI Categorization Client
- App Security Properties
- Product Assignment Log
- Product Assignment Service
- Dependencies Module
- Dashboard DTO
- Backup Restore Write Guard Filter
- Security Config
- Support Config
- Parse Execution Options
- Postgres Integration Test Support
- Phase 15B Product Review And
- AI Receipt Parsing Client
- Request Logging Filter
- API Error Handling Tests
- Phase 11 Backup Restore And
- Json Authentication Entry Point
- Parse Rule Suggestion Status
- Correction Can Apply To Same
- Sync Status
- Product Assignment Transfer Service
- V1 Create Core Schema
- Phase 07 Rest API Contracts
- Persistence Model Behavior Tests
- Synchronize Module
- AI Receipt Parsing Fallback Tests
- Istanbul Browser Coverage Collection
- Scripts Module
- E Bon Restore Runbook
- Compiler Options Cluster 119
- Health Controller
- V25 Add Product Assignment Foundation
- Ebon Backend Application
- Noop AI Product Assignment Client
- Devcontainer Development Services
- Responsive Application Shell
- Package Json
- Backup Configuration
- Backup Restore Lock
- Receipt Item Repository
- V16 Add AI Parsing Fallback
- Paperless Raw Text Reparse Implementation
- Phase 14 Open Router AI
- Rewe Simple Weighted Item Receipt
- Product Family Seeding Implementation Plan
- Bearer Token API Client
- V26 Seed Product Families And
- V27 Seed Additional Product Families
- V3 Enforce Category Source Consistency
- V6 Add AI Categorization Suggestions
- Jawoll Card Payment And Tax
- Mc Donald S Dot Decimal
- Chromedriver Module
- Init Devcontainer Sh Script
- Edgedriver Module
- E Bon Web Html Document
- Jsdom Module
- Nyc Module
- Testing Library React
- Testing Library User Event
- Types React
- Types React Dom
- Typescript Module
- Vite Plugin Istanbul
- Vite Env D
- Edeka Price Times Quantity And
- Rewe Earned Bonus Receipt Fixture
- Paperless Ngx Synchronization
- De Ebon Ebon Backend

## God Nodes (most connected - your core abstractions)
1. `ReceiptItem` - 188 edges
2. `RuleBasedReceiptParser` - 114 edges
3. `ApiClient` - 108 edges
4. `Receipt` - 101 edges
5. `ProductFamily` - 77 edges
6. `Category` - 68 edges
7. `ProductVariant` - 67 edges
8. `ReceiptItemRepository` - 67 edges
9. `ReceiptApiService` - 62 edges
10. `BackupService` - 50 edges

## Surprising Connections (you probably didn't know these)
- `Incremental Redesign Sequence` --semantically_similar_to--> `Incremental AI Implementation Workflow`  [INFERRED] [semantically similar]
  docs/superpowers/specs/2026-07-11-ebon-ui-redesign-design.md → prompts/README.md
- `Previewed and Confirmed Mutation` --semantically_similar_to--> `Complete-Pagination TAG_REMOVED Safety`  [INFERRED] [semantically similar]
  docs/superpowers/specs/2026-07-11-ebon-ui-redesign-design.md → prompts/phase-04-paperless-sync.md
- `dm Multipack and Tax Suffix Fixture` --conceptually_related_to--> `Conservative Product Assignment`  [INFERRED]
  backend/src/test/resources/corpus/dm_realistic_multipack_tax_suffix.txt → ebon-specification.md
- `C&A Stale Detail Amount OCR Fixture` --implements--> `Receipt Parser Test Corpus`  [INFERRED]
  backend/src/test/resources/corpus/ca_clothing_detail_amount_stale_ocr.txt → ebon-specification.md
- `C&A OCR Total Fallback Fixture` --implements--> `Receipt Parser Test Corpus`  [INFERRED]
  backend/src/test/resources/corpus/ca_clothing_ocr_total_fallback.txt → ebon-specification.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Runtime Service Topology** — docker_compose_frontend_service, docker_compose_backend_service, docker_compose_postgresql_service [EXTRACTED 1.00]
- **Receipt Parsing Fixture Suite** — backend_src_test_resources_corpus_aldi_discount_discount_fixture, backend_src_test_resources_corpus_ca_clothing_detail_amount_stale_ocr_stale_detail_amount_fixture, backend_src_test_resources_corpus_ca_clothing_ocr_total_fallback_ocr_total_fallback_fixture, backend_src_test_resources_corpus_dm_bonus_bonus_fixture, backend_src_test_resources_corpus_dm_payback_purchase_points_purchase_points_fixture, backend_src_test_resources_corpus_dm_realistic_multipack_tax_suffix_multipack_tax_fixture, backend_src_test_resources_corpus_dm_zeilenstorno_removes_previous_line_cancellation_fixture, backend_src_test_resources_corpus_edeka_counter_item_amount_next_line_counter_item_fixture [INFERRED 0.95]
- **Receipt Parser Edge-Case Fixtures** — backend_src_test_resources_corpus_edeka_price_x_quantity_payback_edeka_receipt_fixture, backend_src_test_resources_corpus_jawoll_card_payment_tax_table_jawoll_receipt_fixture, backend_src_test_resources_corpus_lidl_multiline_items_lidl_multiline_receipt_fixture, backend_src_test_resources_corpus_mcdonalds_dot_decimal_mcdonalds_dot_decimal_fixture, backend_src_test_resources_corpus_mcdonalds_mobile_order_email_mcdonalds_mobile_order_fixture, backend_src_test_resources_corpus_parse_error_missing_total_missing_total_parse_error_fixture, backend_src_test_resources_corpus_rewe_bonus_earned_ignores_current_balance_rewe_bonus_receipt_fixture, backend_src_test_resources_corpus_rewe_realistic_weight_after_price_rewe_weight_receipt_fixture, backend_src_test_resources_corpus_rewe_simple_rewe_simple_receipt_fixture, backend_src_test_resources_corpus_star_fuel_multiline_eur_star_fuel_receipt_fixture [INFERRED 0.95]
- **Frontend Dual Coverage Strategy** — docs_superpowers_plans_2026_06_22_frontend_coverage_vitest_coverage_gate, docs_superpowers_plans_2026_06_22_selenium_e2e_coverage_istanbul_browser_coverage, docs_superpowers_specs_2026_06_22_frontend_coverage_design_frontend_coverage_design, docs_superpowers_specs_2026_06_22_selenium_e2e_coverage_design_mock_only_browser_coverage [INFERRED 0.95]
- **Safe Data Change Workflows** — docs_restore_runbook_backup_dry_run, docs_restore_runbook_transactional_restore, docs_superpowers_specs_2026_06_19_paperless_raw_text_reparse_design_raw_text_status_contract, docs_agent_workflows_secret_handling_workflow [INFERRED 0.85]
- **Incremental Foundation Phases** — prompts_phase_01_devcontainer_devcontainer_foundation, prompts_phase_02_backend_skeleton_backend_security_skeleton, prompts_phase_03_database_migrations_persistence_foundation, prompts_phase_04_paperless_sync_paperless_sync, prompts_phase_05_parser_corpus_rule_based_parser, prompts_phase_06_categorization_categorization_pipeline [EXTRACTED 1.00]
- **Redesign Shared Interaction Model** — docs_superpowers_specs_2026_07_11_ebon_ui_redesign_design_application_shell, docs_superpowers_specs_2026_07_11_ebon_ui_redesign_design_shared_page_primitives, docs_superpowers_specs_2026_07_11_ebon_ui_redesign_design_stable_navigation_context, docs_superpowers_specs_2026_07_11_ebon_ui_redesign_design_accessible_semantic_status [EXTRACTED 1.00]
- **Secured API and Secret Safety** — prompts_phase_07_rest_api_contracts_masked_secret_update, prompts_phase_08_frontend_shell_bearer_api_client, prompts_phase_12_real_integration_hardening_secret_safe_integration, prompts_phase_13_ci_e2e_operations_mock_only_ci_e2e [INFERRED 0.95]
- **Safe Transactional Administrative Operations** — prompts_phase_10_search_reports_settings_transactional_data_maintenance, prompts_phase_11_backup_restore_restore_dry_run, prompts_phase_11_backup_restore_transactional_restore, prompts_phase_11_backup_restore_backup_write_lock, prompts_phase_15b_product_review_maintenance_preview_before_mutation, prompts_phase_15c_product_price_comparison_reversible_price_exclusion [INFERRED 0.95]
- **Product Assignment Review and Price Lifecycle** — prompts_phase_15a_product_foundation_product_family_variant_model, prompts_phase_15a_product_foundation_trusted_assignment_history, prompts_phase_15b_product_review_maintenance_product_review_queue, prompts_phase_15b_product_review_maintenance_product_merge_split, prompts_phase_15c_product_price_comparison_normalized_unit_price, prompts_phase_15c_product_price_comparison_reversible_price_exclusion [INFERRED 0.95]

## Communities (183 total, 33 thin omitted)

### Community 0 - "Types Module"
Cohesion: 0.03
Nodes (80): ApiClient, DownloadedFile, filenameFromContentDisposition(), productPriceQuery(), toClientError(), TokenProvider, toQuery(), isMockApiEnabled() (+72 more)

### Community 1 - "Products API Contract Tests"
Cohesion: 0.06
Nodes (36): HttpClient, HttpResponse, SpringBootTest, Test, TestPropertySource, OpenApiDisabledConfigurationTests, HttpClient, HttpResponse (+28 more)

### Community 2 - "Receipt API Service"
Cohesion: 0.06
Nodes (33): PaperlessRawTextStatus, CHANGED, UNAVAILABLE, UNCHANGED, Schema, PaperlessRawTextStatusDto, Autowired, PaperlessClient (+25 more)

### Community 3 - "Category Module"
Cohesion: 0.08
Nodes (20): CategoryIconDto, CategoryRequest, CategoryApiService, Service, Transactional, CategoryIconRegistry, Component, CategoryManagementService (+12 more)

### Community 4 - "Search Page"
Cohesion: 0.06
Nodes (39): activeTitle(), AppShell(), AppShellProps, isNavigationActive(), NavigationItem, NavigationLink(), pathFromRoute(), navigation (+31 more)

### Community 5 - "Products Controller"
Cohesion: 0.08
Nodes (23): ProductAssignmentRunRequest, ProductAssignmentRunResponse, ProductFamilyDto, ProductFamilyRequest, ProductRuleApplyRequest, ProductRuleDto, ProductRuleRequest, ProductVariantDto (+15 more)

### Community 6 - "Response Status Exception"
Cohesion: 0.10
Nodes (15): ProductChangePreviewDto, ProductFamilyMergeApplyRequest, ProductFamilyMergeRequest, ProductFamilySplitApplyRequest, ProductFamilySplitRequest, ProductVariantMergeApplyRequest, ProductVariantMergeRequest, ProductVariantSplitApplyRequest (+7 more)

### Community 7 - "Product Assignment Log Repository"
Cohesion: 0.10
Nodes (26): DataMaintenanceController, Operation, PostMapping, RestController, SecurityRequirement, Tag, DataMaintenanceResetRequest, DataMaintenanceResultDto (+18 more)

### Community 8 - "Add Item"
Cohesion: 0.13
Nodes (7): AiProductAssignmentClient, AiProductAssignmentRequest, AiProductAssignmentResponse, AiProductCandidate, Test, ProductAssignmentServiceTests, Test

### Community 9 - "Categorization Rule Management Service"
Cohesion: 0.10
Nodes (23): CategorizationRulesController, DeleteMapping, GetMapping, Operation, PostMapping, PutMapping, ResponseStatus, RestController (+15 more)

### Community 10 - "Receipt Module"
Cohesion: 0.09
Nodes (17): ParseRuleSuggestionReceiptContextDto, DeleteReason, TAG_REMOVED, USER_DELETED, ParseSource, AI, MANUAL_CORRECTED, RULE (+9 more)

### Community 11 - "Categorization Rule"
Cohesion: 0.07
Nodes (19): CategorizationRuleMatcher, Component, CategorizationRule, Entity, PrePersist, Table, RuleMatchField, DESCRIPTION (+11 more)

### Community 12 - "Dashboard Page"
Cohesion: 0.07
Nodes (39): CategoryChart(), CategoryChartProps, ApiClientError, currencyFormatter, dateFormatter, dateTimeFormatter, formatCurrency(), formatDate() (+31 more)

### Community 13 - "Product Review Service"
Cohesion: 0.08
Nodes (22): AssertTrue, ProductAssignmentCorrectionRequest, ProductReviewItemDto, ProductRuleSuggestionAcceptRequest, ProductRuleSuggestionAcceptResponse, ProductAssignmentSource, AI, HISTORY (+14 more)

### Community 14 - "Paperless Properties"
Cohesion: 0.08
Nodes (16): Component, ConfigurationProperties, PaperlessProperties, PaperlessDocumentPage, PaperlessDocumentResponse, Builder, Component, Override (+8 more)

### Community 15 - "Product Price Controller"
Cohesion: 0.09
Nodes (20): ProductPriceExclusionRequest, Schema, ProductPriceGrouping, STORE, STORE_BRANCH, ProductPriceObservationDto, ProductPriceReportDto, ProductPriceStatisticsDto (+12 more)

### Community 17 - "Product Price Service"
Cohesion: 0.13
Nodes (8): ProductPriceFilter, PageResponse, Service, Transactional, PricedItem, ProductPriceService, StoreKey, Test

### Community 18 - "Receipt Item"
Cohesion: 0.12
Nodes (5): Entity, PrePersist, PreUpdate, Table, ReceiptItem

### Community 19 - "Receipts Page"
Cohesion: 0.07
Nodes (33): CategorySourceBadge(), DeleteReasonBadge(), ParseStatusBadge(), formatPercent(), AiCategorizationRejectionReason, CategorySource, DeleteReason, PaperlessRawTextStatus (+25 more)

### Community 20 - "Reports Page"
Cohesion: 0.07
Nodes (26): reportQuery(), BonusReportDTO, ProductFamilyDTO, ProductFamilyRequest, ProductVariantDTO, ProductVariantRequest, ReportByPeriodDTO, ReportByStoreDTO (+18 more)

### Community 21 - "Receipt API Service Cluster 21"
Cohesion: 0.11
Nodes (19): ReceiptDto, Schema, ReceiptItemDto, ReceiptUpdateRequest, DeleteMapping, GetMapping, Operation, PostMapping (+11 more)

### Community 22 - "Get Id"
Cohesion: 0.15
Nodes (7): PaperlessDocument, ExtendWith, Specification, SuppressWarnings, Test, ReceiptApiServiceTests, Test

### Community 23 - "Categorization Service Tests"
Cohesion: 0.15
Nodes (13): CategorizationServiceTests, FakeAiCategorizationClient, FakeAiCategorizationClientConfig, Bean, BeforeEach, JdbcTemplate, Override, Primary (+5 more)

### Community 24 - "AI Categorization Properties"
Cohesion: 0.10
Nodes (14): Builder, Component, JsonNode, ObjectMapper, Override, RestClient, RestClientException, OpenRouterAiCategorizationClient (+6 more)

### Community 25 - "Receipt API Contract Tests"
Cohesion: 0.13
Nodes (15): FakePaperlessClientConfig, Bean, BeforeEach, Builder, HttpClient, HttpResponse, JdbcTemplate, ObjectMapper (+7 more)

### Community 26 - "Backup Service"
Cohesion: 0.12
Nodes (12): BackupTableValidationDto, BackupValidationReportDto, BackupArchive, BackupColumn, BackupService, BackupTable, JdbcTemplate, MultipartFile (+4 more)

### Community 27 - "Receipt Item Update Request"
Cohesion: 0.07
Nodes (11): AssertTrue, Schema, ReceiptItemCreateRequest, AssertTrue, Schema, ReceiptItemUpdateRequest, ReceiptItemCreateRequest, CategorySource (+3 more)

### Community 28 - "Parse Rule"
Cohesion: 0.09
Nodes (12): Autowired, Component, Matcher, Pattern, Entity, PrePersist, Table, ParseRule (+4 more)

### Community 29 - "Settings Page"
Cohesion: 0.07
Nodes (22): CategoryIcon(), IconComponent, iconComponents, isKnownCategoryIcon(), CategoryDTO, CategoryRequest, ParseRuleSuggestionReceiptContextDTO, ReparseScope (+14 more)

### Community 30 - "Parse Items"
Cohesion: 0.10
Nodes (4): GermanNumberParser, ParsedReceiptItem, QuantityDetails, StarFuelDetails

### Community 31 - "Products Page"
Cohesion: 0.08
Nodes (24): CategorizationRuleApplyResponse, ProductAssignmentSource, ProductAssignmentStatus, choiceClass(), commonPrefixLength(), compactSearch(), CorrectionDialog(), defaultFilters (+16 more)

### Community 32 - "Query API Service"
Cohesion: 0.12
Nodes (12): ByPeriod, ByStore, ReportDto, TopItem, TopProduct, CategoryKey, Service, Specification (+4 more)

### Community 33 - "Product Rule"
Cohesion: 0.13
Nodes (7): Entity, PrePersist, PreUpdate, Table, ProductRule, Component, ProductRuleMatcher

### Community 34 - "Mock API"
Cohesion: 0.06
Nodes (31): aiParsingLogs, bonusReport, categories, categoryIcons, categoryReport, dashboard, maintenanceResult(), mockRequest() (+23 more)

### Community 35 - "Backup Service Tests"
Cohesion: 0.17
Nodes (8): AfterEach, BackupServiceTests, BeforeEach, JdbcTemplate, MultipartFile, SpringBootTest, Test, TestPropertySource

### Community 36 - "AI Categorization Log"
Cohesion: 0.09
Nodes (15): AiSuggestionDto, Schema, Service, Transactional, AiCategorizationLog, Entity, PrePersist, Table (+7 more)

### Community 37 - "AI Parsing Properties"
Cohesion: 0.11
Nodes (8): AiParsingProperties, Component, ConfigurationProperties, AiParsingSettingsService, Service, AiParsingTextMode, FULL_TEXT, MINIMIZED

### Community 38 - "Parse Rule Suggestion"
Cohesion: 0.12
Nodes (6): ReparseScope, Entity, PrePersist, PreUpdate, Table, ParseRuleSuggestion

### Community 39 - "API Controller Web Mvc Tests"
Cohesion: 0.12
Nodes (15): ByCategory, CategoryDeletionResult, DEACTIVATED, HARD_DELETED, ApiControllerWebMvcTests, FakeServiceConfig, Bean, Builder (+7 more)

### Community 40 - "AI Parsing Controller"
Cohesion: 0.14
Nodes (12): AiParsingController, GetMapping, Operation, PostMapping, RestController, SecurityRequirement, Tag, FixtureExportDto (+4 more)

### Community 41 - "Paperless Sync Service Tests"
Cohesion: 0.14
Nodes (12): FakePaperlessClient, FakePaperlessClientConfig, Bean, BeforeEach, JdbcTemplate, Override, Primary, SpringBootTest (+4 more)

### Community 42 - "Receipt Parser Test Corpus"
Cohesion: 0.08
Nodes (29): eBon-Web Agent Project Contract, Spring Boot Backend Configuration, ALDI Discount Receipt Fixture, C&A Stale Detail Amount OCR Fixture, C&A OCR Total Fallback Fixture, dm Bonus Receipt Fixture, dm PAYBACK Purchase Points Fixture, dm Multipack and Tax Suffix Fixture (+21 more)

### Community 43 - "Settings Service"
Cohesion: 0.15
Nodes (4): Autowired, Service, Transactional, SettingsService

### Community 44 - "Categories Controller"
Cohesion: 0.14
Nodes (16): CategoriesController, DeleteMapping, GetMapping, Operation, PatchMapping, PostMapping, PutMapping, ResponseStatus (+8 more)

### Community 45 - "Parse Rule Validation Status"
Cohesion: 0.12
Nodes (14): Component, Matcher, ParseRuleSuggestionValidator, ValidationResult, Service, ParseRuleSuggestionWriter, ParseRuleValidationStatus, COLLISION_RISK (+6 more)

### Community 47 - "AI Parsing Log"
Cohesion: 0.14
Nodes (4): AiParsingLog, Entity, PrePersist, Table

### Community 48 - "Categorization Service"
Cohesion: 0.13
Nodes (6): AiCategorizationBatchRequest, AiCategorizationBatchResponse, AiCategorizationClient, AiCategorizationItem, AiCategorizationSuggestion, CategorizationService

### Community 49 - "Product Family"
Cohesion: 0.13
Nodes (6): Entity, PrePersist, PreUpdate, Table, ProductFamily, BeforeEach

### Community 50 - "Incremental AI Implementation Workflow"
Cohesion: 0.10
Nodes (26): Incremental Redesign Sequence, Information-First Interface, Refined Current UI Redesign, Previewed and Confirmed Mutation, No Feature Expansion During Repair, Scoped Current-Phase Error Repair, Phase 1 Devcontainer Foundation, Safe Example Secrets (+18 more)

### Community 51 - "AI Parsing Trigger"
Cohesion: 0.10
Nodes (17): AiParsingLogDto, AiParsingSummaryDto, AiParsingStatus, DISABLED, FAILED, INVALID_RESPONSE, LOW_CONFIDENCE, NO_API_KEY (+9 more)

### Community 52 - "AI Parsing API Service"
Cohesion: 0.12
Nodes (12): FixturePreviewDto, Page, Schema, PageResponse, ParseRuleSuggestionItemDto, AiParsingApiService, ObjectMapper, Service (+4 more)

### Community 53 - "Version Service"
Cohesion: 0.14
Nodes (14): SystemInfoDto, GetMapping, Operation, RestController, Tag, SystemController, Bean, Configuration (+6 more)

### Community 54 - "Query API Service Tests"
Cohesion: 0.22
Nodes (10): FakeSyncServiceConfig, ItemSpec, Bean, Primary, SpringBootTest, Test, TestConfiguration, TestPropertySource (+2 more)

### Community 55 - "Settings Connection Test Response"
Cohesion: 0.16
Nodes (11): SettingsConnectionTestRequest, Target, OPENROUTER, PAPERLESS, SettingsConnectionTestResponse, Builder, Override, Service (+3 more)

### Community 56 - "Global Exception Handler"
Cohesion: 0.30
Nodes (11): ApiError, GlobalExceptionHandler, HttpServletRequest, HttpStatus, Logger, MethodArgumentNotValidException, ResponseEntity, DataIntegrityViolationException (+3 more)

### Community 57 - "Reports Controller"
Cohesion: 0.28
Nodes (8): GetMapping, Operation, ResponseEntity, RestController, SecurityRequirement, Tag, Validated, ReportsController

### Community 58 - "Rolling Backup Properties"
Cohesion: 0.14
Nodes (7): ConfigurationProperties, Validated, RollingBackupProperties, Logger, Scheduled, Service, RollingBackupService

### Community 59 - "Try Fallback"
Cohesion: 0.16
Nodes (7): AiParsingFallbackService, ObjectMapper, Service, AiReceiptJsonParseResult, ParsedReceipt, ReceiptParseResult, ReceiptParseValidator

### Community 60 - "Open Router AI Receipt Parsing"
Cohesion: 0.14
Nodes (12): AiParsingSettings, AiReceiptParsingPrompt, Builder, Component, Override, RestClient, RestClientException, OpenRouterAiReceiptParsingClient (+4 more)

### Community 61 - "Compiler Options"
Cohesion: 0.09
Nodes (23): compilerOptions, allowJs, allowSyntheticDefaultImports, esModuleInterop, forceConsistentCasingInFileNames, isolatedModules, jsx, lib (+15 more)

### Community 62 - "Parse Rule Type"
Cohesion: 0.11
Nodes (15): PutMapping, ParseRuleSuggestionAcceptRequest, ReparseScope, ALL_PARSE_ERROR, CURRENT_RECEIPT, NONE, PARSE_ERROR_BY_STORE, ParseRuleSuggestionUpdateRequest (+7 more)

### Community 63 - "Sync Controller"
Cohesion: 0.15
Nodes (12): GetMapping, Operation, PostMapping, ResponseEntity, RestController, Tag, Validated, SyncController (+4 more)

### Community 64 - "Sync Log"
Cohesion: 0.17
Nodes (5): Entity, PrePersist, Table, SyncLog, Transactional

### Community 65 - "Product Variant"
Cohesion: 0.15
Nodes (5): Entity, PrePersist, PreUpdate, Table, ProductVariant

### Community 66 - "Sync Log Entry"
Cohesion: 0.12
Nodes (9): Entity, PrePersist, Table, SyncLogEntry, SyncLogEntryAction, IMPORTED, SKIPPED, TAG_REMOVED (+1 more)

### Community 67 - "App Setting"
Cohesion: 0.17
Nodes (9): AppSetting, Entity, PrePersist, PreUpdate, Table, Test, SettingsServiceTests, AiParsingSettingsServiceTests (+1 more)

### Community 68 - "Parse With Metadata"
Cohesion: 0.31
Nodes (4): AiReceiptJsonParser, Component, JsonNode, ObjectMapper

### Community 69 - "Quote Module"
Cohesion: 0.16
Nodes (8): NormalizedQuantity, PriceQuote, ProductPriceCalculator, Component, NormalizedQuantity, ProductUnitNormalizer, Test, ProductPriceCalculatorTests

### Community 70 - "Rule Match Type"
Cohesion: 0.14
Nodes (11): ProductRulePreviewRequest, ProductRulePreviewResponse, ProductRuleSuggestionDto, ProductRuleSuggestionRequest, ProductRulePreviewResponse, RuleMatchType, CONTAINS, ENDS_WITH (+3 more)

### Community 71 - "Paperless Sync Service"
Cohesion: 0.17
Nodes (8): BackupRestoreLockedException, Page, Pageable, SyncLogRepository, Logger, Service, TaskExecutor, PaperlessSyncService

### Community 72 - "Smoke Mjs"
Cohesion: 0.16
Nodes (14): writeBrowserCoverage(), baseUrl, coverageDirectory, coverageOutputFile, frontendRoot, chromedriver, clickButton(), clickNav() (+6 more)

### Community 73 - "API Error Factory"
Cohesion: 0.19
Nodes (11): AccessDeniedException, AccessDeniedHandler, ApiErrorFactory, Component, HttpServletRequest, HttpStatus, HttpServletRequest, HttpServletResponse (+3 more)

### Community 74 - "Receipt Parser Corpus Tests"
Cohesion: 0.25
Nodes (7): Arguments, JsonNode, ObjectMapper, ParameterizedTest, Test, ReceiptParserCorpusTests, MethodSource

### Community 75 - "Reparse Receipt"
Cohesion: 0.14
Nodes (5): RawTextSource, PAPERLESS, STORED, Test, ReceiptParseApplierTests

### Community 76 - "Components Json"
Cohesion: 0.11
Nodes (17): aliases, components, hooks, lib, ui, utils, iconLibrary, rsc (+9 more)

### Community 77 - "App Module"
Cohesion: 0.15
Nodes (14): App(), navigation, normalizeHash(), paramsFromRoute(), pathFromRoute(), ProductsPage, receiptIdFromRoute(), ReceiptsPage (+6 more)

### Community 78 - "Dev Dependencies"
Cohesion: 0.12
Nodes (17): cross-env, devDependencies, cross-env, selenium-webdriver, @testing-library/jest-dom, @types/node, vite, @vitejs/plugin-react (+9 more)

### Community 79 - "Backup Controller"
Cohesion: 0.23
Nodes (10): BackupController, GetMapping, MultipartFile, Operation, PostMapping, ResponseEntity, RestController, SecurityRequirement (+2 more)

### Community 80 - "Settings Controller"
Cohesion: 0.21
Nodes (10): SettingsDto, GetMapping, Operation, PostMapping, PutMapping, RestController, SecurityRequirement, Tag (+2 more)

### Community 81 - "Receipt Parser Properties"
Cohesion: 0.27
Nodes (5): Component, ConfigurationProperties, ReceiptParserProperties, Test, ReceiptParserPropertiesTests

### Community 82 - "Migration Seeds Store Specific Coca"
Cohesion: 0.28
Nodes (6): Test, ProductSeedMigrationTests, Connection, DynamicPropertyRegistry, DynamicPropertySource, PostgreSQLContainer

### Community 83 - "Create Automatic Backup"
Cohesion: 0.21
Nodes (6): BackupFile, ExtendWith, Test, RollingBackupServiceTests, FileTime, ZipOutputStream

### Community 84 - "Once Per Request Filter"
Cohesion: 0.22
Nodes (10): Component, FilterChain, HttpServletRequest, HttpServletResponse, Order, Override, TraceIdFilter, Test (+2 more)

### Community 85 - "Backend Skeleton Security Tests"
Cohesion: 0.30
Nodes (7): BackendSkeletonSecurityTests, HttpClient, HttpResponse, ObjectMapper, SpringBootTest, Test, TestPropertySource

### Community 87 - "Search Controller"
Cohesion: 0.23
Nodes (8): SearchResultDto, GetMapping, Operation, RestController, SecurityRequirement, Tag, Validated, SearchController

### Community 88 - "Receipt Items Controller"
Cohesion: 0.24
Nodes (9): DeleteMapping, Operation, PatchMapping, ResponseStatus, RestController, SecurityRequirement, Tag, Validated (+1 more)

### Community 90 - "App Security Properties"
Cohesion: 0.23
Nodes (7): AppSecurityProperties, ConfigurationProperties, ApiTokenAuthenticationFilter, FilterChain, HttpServletRequest, HttpServletResponse, Override

### Community 91 - "Product Assignment Log"
Cohesion: 0.18
Nodes (4): Entity, PrePersist, Table, ProductAssignmentLog

### Community 92 - "Product Assignment Service"
Cohesion: 0.27
Nodes (4): Pattern, Service, Transactional, ProductAssignmentService

### Community 93 - "Dependencies Module"
Cohesion: 0.15
Nodes (13): dependencies, lucide-react, react, react-dom, recharts, tailwindcss, @tailwindcss/vite, lucide-react (+5 more)

### Community 94 - "Dashboard DTO"
Cohesion: 0.26
Nodes (8): DashboardController, GetMapping, Operation, RestController, SecurityRequirement, Tag, DashboardDto, Bonus

### Community 95 - "Backup Restore Write Guard Filter"
Cohesion: 0.30
Nodes (7): BackupRestoreWriteGuardFilter, Component, FilterChain, HttpServletRequest, HttpServletResponse, ObjectMapper, Override

### Community 96 - "Security Config"
Cohesion: 0.29
Nodes (9): AppOpenApiProperties, ConfigurationProperties, Bean, Configuration, EnableConfigurationProperties, ObjectMapper, SecurityConfig, HttpSecurity (+1 more)

### Community 97 - "Support Config"
Cohesion: 0.29
Nodes (7): Bean, Builder, Configuration, ObjectMapper, TaskExecutor, SupportConfig, Qualifier

### Community 99 - "Postgres Integration Test Support"
Cohesion: 0.29
Nodes (9): EbonBackendApplicationTests, SpringBootTest, Test, JdbcTemplate, SpringBootTest, Transactional, MigrationAndRepositorySmokeTests, PostgresIntegrationTestSupport (+1 more)

### Community 100 - "Phase 15B Product Review And"
Cohesion: 0.18
Nodes (12): NO_PRODUCT Status, Product Family and Variant Model, Phase 15a Product Foundation, Trusted Product Assignment History, Preview Before Product Mutation, Product Merge and Split, Phase 15b Product Review and Maintenance, Product Review Queue (+4 more)

### Community 101 - "AI Receipt Parsing Client"
Cohesion: 0.24
Nodes (6): AiReceiptParsingClient, AiReceiptParsingClientResponse, Component, Override, NoopAiReceiptParsingClient, ConditionalOnMissingBean

### Community 102 - "Request Logging Filter"
Cohesion: 0.33
Nodes (8): Component, FilterChain, HttpServletRequest, HttpServletResponse, Logger, Order, Override, RequestLoggingFilter

### Community 103 - "API Error Handling Tests"
Cohesion: 0.29
Nodes (5): ApiErrorHandlingTests, DummyEndpoint, MethodArgumentNotValidException, SuppressWarnings, Test

### Community 104 - "Phase 11 Backup Restore And"
Cohesion: 0.18
Nodes (11): Phase 11 Backup Restore and Dry-Run, Backup and Restore Write Lock, Restore Dry-Run, Transactional Restore, Docker Compose Smoke Test, Phase 12 Real Integration and Hardening, Secret-Safe Real Integration, Central Software Version (+3 more)

### Community 105 - "Json Authentication Entry Point"
Cohesion: 0.33
Nodes (7): AuthenticationEntryPoint, AuthenticationException, HttpServletRequest, HttpServletResponse, ObjectMapper, Override, JsonAuthenticationEntryPoint

### Community 106 - "Parse Rule Suggestion Status"
Cohesion: 0.20
Nodes (4): ParseRuleSuggestionStatus, ACCEPTED, OPEN, REJECTED

### Community 109 - "Sync Status"
Cohesion: 0.24
Nodes (6): SyncStatus, FAILED, RUNNING, SUCCESS, SyncLogDto, SyncRunResult

### Community 111 - "V1 Create Core Schema"
Cohesion: 0.36
Nodes (9): ai_categorization_log, app_settings, categorization_rule, category, parse_rule, receipt, receipt_item, sync_log (+1 more)

### Community 112 - "Phase 07 Rest API Contracts"
Cohesion: 0.20
Nodes (10): Masked Secret Update Safety, Paperless Raw Text Status Contract, Phase 07 REST API Contracts, Uncategorized Null Contract, Manual Edit Protection, Raw Text Reparse Decision Dialog, Phase 09 Receipts UI, Phase 10 Search Reports and Settings (+2 more)

### Community 113 - "Persistence Model Behavior Tests"
Cohesion: 0.39
Nodes (3): PrePersist, Test, PersistenceModelBehaviorTests

### Community 114 - "Synchronize Module"
Cohesion: 0.33
Nodes (5): Component, Logger, Scheduled, PaperlessSyncScheduler, ConditionalOnProperty

### Community 116 - "Istanbul Browser Coverage Collection"
Cohesion: 0.22
Nodes (9): Frontend Coverage Implementation Plan, Vitest 50 Percent Coverage Gate, Istanbul Browser Coverage Collection, Selenium E2E Coverage Implementation Plan, eBon UI Redesign Implementation Plan, Frontend Information Parity, Frontend Coverage Design, Mock-Only Browser Coverage (+1 more)

### Community 117 - "Scripts Module"
Cohesion: 0.22
Nodes (9): scripts, build, dev, dev:e2e, e2e, e2e:coverage, preview, test (+1 more)

### Community 118 - "E Bon Restore Runbook"
Cohesion: 0.25
Nodes (8): Missing Total Parse Error Fixture, Agent Workflows, Parser Fixture Workflow, Secret Handling Workflow, Backup Restore Dry-Run, Masked Secret Reconfiguration, eBon Restore Runbook, Transactional Restore

### Community 119 - "Compiler Options Cluster 119"
Cohesion: 0.25
Nodes (7): compilerOptions, allowSyntheticDefaultImports, module, moduleResolution, skipLibCheck, include, vite.config.ts

### Community 120 - "Health Controller"
Cohesion: 0.48
Nodes (5): HealthController, GetMapping, Operation, RestController, Tag

### Community 121 - "V25 Add Product Assignment Foundation"
Cohesion: 0.80
Nodes (5): product_assignment_log, product_family, product_rule, product_variant, receipt_item

### Community 122 - "Ebon Backend Application"
Cohesion: 0.60
Nodes (3): EbonBackendApplication, EnableScheduling, SpringBootApplication

### Community 123 - "Noop AI Product Assignment Client"
Cohesion: 0.50
Nodes (3): Component, Override, NoopAiProductAssignmentClient

### Community 124 - "Devcontainer Development Services"
Cohesion: 0.40
Nodes (5): eBon Devcontainer Rules, Devcontainer Development Services, Backend Service, Frontend Service, PostgreSQL Service

### Community 125 - "Responsive Application Shell"
Cohesion: 0.40
Nodes (5): Accessible Semantic Status, Responsive Application Shell, Shared Page Primitives, Stable Navigation Context, System Light and Dark Color Scheme

### Community 126 - "Package Json"
Cohesion: 0.40
Nodes (4): name, private, type, version

### Community 127 - "Backup Configuration"
Cohesion: 0.83
Nodes (3): BackupConfiguration, Configuration, EnableConfigurationProperties

### Community 130 - "V16 Add AI Parsing Fallback"
Cohesion: 1.00
Nodes (3): ai_parsing_log, parse_rule_suggestion, receipt

### Community 131 - "Paperless Raw Text Reparse Implementation"
Cohesion: 0.50
Nodes (4): Paperless Raw Text Reparse Implementation Plan, Paperless Raw Text Preflight, Paperless Raw Text Reparse Design, Raw Text Status Contract

### Community 132 - "Phase 14 Open Router AI"
Cohesion: 0.50
Nodes (4): AI Parse Adoption Gate, Phase 14 OpenRouter AI Parsing Fallback, Parse Rule Suggestion Workflow, Validated Hybrid Parser

### Community 133 - "Rewe Simple Weighted Item Receipt"
Cohesion: 0.67
Nodes (3): LIDL Multiline Item Receipt Fixture, REWE Weight-After-Price Receipt Fixture, REWE Simple Weighted Item Receipt Fixture

### Community 134 - "Product Family Seeding Implementation Plan"
Cohesion: 0.67
Nodes (3): Product Family Seeding Implementation Plan, Product Family Seeding Design, Size-Safe Product Variants

### Community 135 - "Bearer Token API Client"
Cohesion: 0.67
Nodes (3): Bearer Token API Client, Phase 08 Frontend Shell, Vite API Proxy

## Knowledge Gaps
- **307 isolated node(s):** `init-devcontainer.sh script`, `de.ebon:ebon-backend`, `UNCHANGED`, `CHANGED`, `UNAVAILABLE` (+302 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **33 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ReceiptItem` connect `Receipt Item` to `Receipt Item Repository`, `Receipt API Service`, `Category Module`, `Response Status Exception`, `Product Assignment Log Repository`, `Add Item`, `Categorization Rule Management Service`, `Receipt Module`, `Categorization Rule`, `Product Review Service`, `Product Price Controller`, `Product Price Service`, `Receipt API Service Cluster 21`, `Get Id`, `Categorization Service Tests`, `Receipt API Contract Tests`, `Receipt Item Update Request`, `Query API Service`, `Product Rule`, `AI Categorization Log`, `Categorization Service`, `Product Family`, `AI Parsing API Service`, `Product Variant`, `Quote Module`, `Product Assignment Log`, `Product Assignment Service`, `Postgres Integration Test Support`, `Correction Can Apply To Same`, `Product Assignment Transfer Service`?**
  _High betweenness centrality (0.085) - this node is a cross-community bridge._
- **Why does `Receipt` connect `Receipt Module` to `Receipt API Service`, `Product Assignment Log Repository`, `Add Item`, `Receipt Item`, `Receipt API Service Cluster 21`, `Get Id`, `Categorization Service Tests`, `Receipt API Contract Tests`, `Query API Service`, `AI Categorization Log`, `Parse Rule Suggestion`, `Parse Rule Validation Status`, `AI Parsing Log`, `Categorization Service`, `AI Parsing Trigger`, `AI Parsing API Service`, `Query API Service Tests`, `Try Fallback`, `Sync Log Entry`, `Reparse Receipt`, `AI Parsing Fallback Service Tests`, `Product Assignment Service`, `Persistence Model Behavior Tests`?**
  _High betweenness centrality (0.062) - this node is a cross-community bridge._
- **Why does `PostgresIntegrationTestSupport` connect `Postgres Integration Test Support` to `Products API Contract Tests`, `Receipt API Service`, `Backup Service Tests`, `API Controller Web Mvc Tests`, `Categorization Rule Management Service`, `Paperless Sync Service Tests`, `Categorization Rule`, `Migration Seeds Store Specific Coca`, `Backend Skeleton Security Tests`, `Query API Service Tests`, `Categorization Service Tests`, `Receipt API Contract Tests`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **What connects `init-devcontainer.sh script`, `de.ebon:ebon-backend`, `UNCHANGED` to the rest of the system?**
  _307 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Types Module` be split into smaller, more focused modules?**
  _Cohesion score 0.03368244658567239 - nodes in this community are weakly interconnected._
- **Should `Products API Contract Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.06438631790744467 - nodes in this community are weakly interconnected._
- **Should `Receipt API Service` be split into smaller, more focused modules?**
  _Cohesion score 0.061457418788410885 - nodes in this community are weakly interconnected._
