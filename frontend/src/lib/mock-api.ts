import type {
  BackupRestoreResultDTO,
  BackupValidationReportDTO,
  BonusReportDTO,
  CategorizationRuleDTO,
  CategoryDTO,
  CategoryIconDTO,
  DashboardDTO,
  DataMaintenanceResultDTO,
  MessageResponse,
  PageResponse,
  ProductChangePreviewDTO,
  ProductFamilyDTO,
  ProductReviewItemDTO,
  ProductRuleDTO,
  ProductVariantDTO,
  ReceiptDTO,
  ReportByCategoryDTO,
  ReportByPeriodDTO,
  ReportByStoreDTO,
  SearchResultDTO,
  SettingsConnectionTestResponse,
  SettingsDTO,
  SyncLogDTO,
  SyncStatusDTO,
  SystemInfoDTO,
  TopItemReportDTO
} from "@/lib/types";

export function isMockApiEnabled(): boolean {
  return import.meta.env.VITE_EBON_MOCK_API === "true";
}

export async function mockRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const url = new URL(path, "http://mock.local");
  const method = init.method?.toUpperCase() ?? "GET";

  if (url.pathname === "/dashboard") {
    return dashboard as T;
  }
  if (url.pathname === "/system/info") {
    return systemInfo as T;
  }
  if (url.pathname === "/sync/status") {
    return syncStatus as T;
  }
  if (url.pathname === "/sync/trigger" && method === "POST") {
    return { message: "Sync gestartet" } as MessageResponse as T;
  }
  if (url.pathname === "/sync/log") {
    return page(syncLog, 0, 5) as T;
  }
  if (url.pathname === "/categories") {
    return categories as T;
  }
  if (url.pathname === "/categories/icons") {
    return categoryIcons as T;
  }
  if (url.pathname === "/products/families") {
    return method === "GET" ? productFamilies as T : productFamilies[0] as T;
  }
  if (/^\/products\/families\/\d+$/.test(url.pathname)) {
    return productFamilies[0] as T;
  }
  if (url.pathname === "/products/variants") {
    return method === "GET" ? productVariants as T : productVariants[0] as T;
  }
  if (/^\/products\/variants\/\d+$/.test(url.pathname)) {
    return productVariants[0] as T;
  }
  if (url.pathname === "/products/rules") {
    return method === "GET" ? productRules as T : productRules[0] as T;
  }
  if (/^\/products\/rules\/\d+(?:\/apply)?$/.test(url.pathname)) {
    return { changedItemsCount: 1 } as T;
  }
  if (url.pathname === "/products/review") {
    return page(productReviewItems, Number(url.searchParams.get("page") ?? 0), Number(url.searchParams.get("size") ?? 20)) as T;
  }
  if (/^\/products\/review\/\d+\/(?:accept|correct|reject|no-product)$/.test(url.pathname)) {
    return productReviewItems[0] as T;
  }
  if (/^\/products\/review\/\d+\/assignment$/.test(url.pathname)) {
    return undefined as T;
  }
  if (/^\/products\/review\/\d+\/rule-suggestion$/.test(url.pathname)) {
    return { rule: productRules[0], preview: { matchingItemsCount: 2 } } as T;
  }
  if (/^\/products\/review\/\d+\/rule-suggestion\/accept$/.test(url.pathname)) {
    return { rule: productRules[0], changedItemsCount: 1 } as T;
  }
  if (/^\/products\/(?:families|variants)\/(?:merge|split)\/(?:preview|apply)$/.test(url.pathname)) {
    return productChangePreview as T;
  }
  if (url.pathname === "/categorization-rules") {
    return rules as T;
  }
  if (url.pathname === "/settings") {
    return settings as T;
  }
  if (url.pathname === "/settings/test-connection") {
    return {
      target: "PAPERLESS",
      success: true,
      message: "Mock-Verbindung erfolgreich."
    } as SettingsConnectionTestResponse as T;
  }
  if (url.pathname === "/receipts") {
    return page(receipts, Number(url.searchParams.get("page") ?? 0), Number(url.searchParams.get("size") ?? 20)) as T;
  }
  if (/^\/receipts\/\d+$/.test(url.pathname)) {
    return receipts[0] as T;
  }
  if (/^\/receipts\/\d+\/paperless-raw-text-status$/.test(url.pathname)) {
    return { status: "CHANGED" } as T;
  }
  if (/^\/receipts\/\d+\/reparse$/.test(url.pathname) && method === "POST") {
    return receipts[0] as T;
  }
  if (/^\/receipts\/\d+\/ai-parsing-log$/.test(url.pathname)) {
    return aiParsingLogs as T;
  }
  if (url.pathname === "/parser/rule-suggestions") {
    return page(
      parseRuleSuggestions.map((suggestion) => ({ ...suggestion, receiptContext: null })),
      Number(url.searchParams.get("page") ?? 0),
      Number(url.searchParams.get("size") ?? 20)
    ) as T;
  }
  if (/^\/parser\/rule-suggestions\/\d+$/.test(url.pathname)) {
    return parseRuleSuggestions[0] as T;
  }
  if (/^\/parser\/rule-suggestions\/\d+\/accept$/.test(url.pathname) && method === "POST") {
    return { ...parseRuleSuggestions[0], status: "ACCEPTED", acceptedParseRuleId: 99 } as T;
  }
  if (/^\/parser\/rule-suggestions\/\d+\/reject$/.test(url.pathname) && method === "POST") {
    return { ...parseRuleSuggestions[0], status: "REJECTED", rejectionReason: "Mock-Ablehnung" } as T;
  }
  if (url.pathname === "/parser/rule-suggestions/export-migration" && method === "POST") {
    return { filename: "V_next__add_ai_adapted_parse_rules.sql", sql: "-- mock migration" } as T;
  }
  if (url.pathname === "/search") {
    return page(searchResults, 0, 20) as T;
  }
  if (url.pathname === "/reports/by-category") {
    return categoryReport as T;
  }
  if (url.pathname === "/reports/by-period") {
    return periodReport as T;
  }
  if (url.pathname === "/reports/by-store") {
    return storeReport as T;
  }
  if (url.pathname === "/reports/top-items") {
    return topItemsReport as T;
  }
  if (url.pathname === "/reports/bonus") {
    return bonusReport as T;
  }
  if (url.pathname === "/receipts/reparse" && method === "POST") {
    return maintenanceResult("Bons wurden im Mock neu geparst.") as T;
  }
  if (url.pathname === "/admin/data-reset/imported-receipts" && method === "POST") {
    return maintenanceResult("Importierte Bon-Daten wurden im Mock gelöscht.") as T;
  }

  return undefined as T;
}

export async function mockDownload(path: string): Promise<Blob> {
  const content = path.includes("/reports/")
    ? "category,total\nLebensmittel,42.10\n"
    : "mock-backup";
  return new Blob([content], { type: "application/octet-stream" });
}

export async function mockUploadFile<T>(path: string, file: File): Promise<T> {
  const validation: BackupValidationReportDTO = {
    valid: file.name.endsWith(".zip"),
    manifestVersion: "1",
    tables: [
      { name: "categories", recordCount: 3, valid: true },
      { name: "receipts", recordCount: 2, valid: true },
      { name: "receipt_items", recordCount: 3, valid: true }
    ],
    warnings: [],
    errors: file.name.endsWith(".zip") ? [] : ["Datei ist kein ZIP-Backup."]
  };

  if (path === "/backup/restore") {
    return {
      message: "Backup wurde im Mock wiederhergestellt.",
      validation
    } as BackupRestoreResultDTO as T;
  }

  return validation as T;
}

function page<T>(content: T[], pageNumber: number, size: number): PageResponse<T> {
  return {
    content: content.slice(pageNumber * size, pageNumber * size + size),
    page: pageNumber,
    size,
    totalElements: content.length,
    totalPages: Math.max(1, Math.ceil(content.length / size)),
    sortBy: "receiptDate",
    sortDir: "desc"
  };
}

function maintenanceResult(message: string): DataMaintenanceResultDTO {
  return {
    message,
    totalReceipts: 2,
    processedReceipts: 2,
    skippedManualReceipts: 0,
    deletedReceipts: 2,
    deletedSyncLogs: 1
  };
}

const systemInfo: SystemInfoDTO = {
  name: "eBon Expense Tracker",
  version: "0.1.0-SNAPSHOT"
};

const syncStatus: SyncStatusDTO = {
  lastSyncAt: "2026-06-16T07:30:00Z",
  lastSyncStatus: "SUCCESS",
  newDocumentsCount: 2,
  removedDocumentsCount: 0,
  errorCount: 0,
  isSyncing: false
};

const categories: CategoryDTO[] = [
  { id: 1, name: "Salat, Obst & Gemüse", colorHex: "#43A047", icon: "apple", isActive: true, sortOrder: 11, assignedItemsCount: 1 },
  { id: 2, name: "Milchprodukte und Eier", colorHex: "#00838F", icon: "milk", isActive: true, sortOrder: 13, assignedItemsCount: 1 },
  { id: 3, name: "Pfand und Rabatte", colorHex: "#546E7A", icon: "receipt", isActive: true, sortOrder: 90, assignedItemsCount: 1 }
];

const categoryIcons: CategoryIconDTO[] = [
  { value: "shopping-basket", label: "Lebensmittel" },
  { value: "apple", label: "Salat, Obst & Gemüse" },
  { value: "milk", label: "Milchprodukte und Eier" },
  { value: "receipt", label: "Pfand und Rabatte" },
  { value: "tag", label: "Tag / Rabatt" }
];

const productFamilies: ProductFamilyDTO[] = [
  { id: 10, name: "Haferdrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, createdAt: "2026-06-01T10:00:00Z", updatedAt: "2026-06-01T10:00:00Z" },
  { id: 11, name: "Coca-Cola Zero", defaultCategoryId: null, defaultCategoryName: null, isActive: true, createdAt: "2026-06-01T10:00:00Z", updatedAt: "2026-06-01T10:00:00Z" }
];

const productVariants: ProductVariantDTO[] = [
  { id: 20, productFamilyId: 10, productFamilyName: "Haferdrink", name: "Haferdrink 1 l", unitQuantity: 1, unit: "l", packageQuantity: 1, packageDescription: null, totalQuantity: 1, totalUnit: "l", gtin: null, isActive: true },
  { id: 21, productFamilyId: 11, productFamilyName: "Coca-Cola Zero", name: "Coca-Cola Zero 0.5 l", unitQuantity: 0.5, unit: "l", packageQuantity: 1, packageDescription: null, totalQuantity: 0.5, totalUnit: "l", gtin: null, isActive: true }
];

const productRules: ProductRuleDTO[] = [
  { id: 30, productFamilyId: 10, productFamilyName: "Haferdrink", productVariantId: 20, productVariantName: "Haferdrink 1 l", storeName: "dm", matchType: "EXACT", matchValue: "Haferdrink Barista", priority: 100, isActive: true }
];

const productReviewItems: ProductReviewItemDTO[] = [
  { receiptItemId: 11, receiptId: 1, receiptDate: "2026-06-15", storeName: "REWE", storeBranch: "Mockstraße 1", description: "Haferdrink Barista", quantity: 1, unit: "l", unitPrice: 1.79, totalPrice: 1.79, categoryId: 2, categoryName: "Milchprodukte und Eier", currentProductFamilyId: null, currentProductFamilyName: null, currentProductVariantId: null, currentProductVariantName: null, suggestedProductFamilyId: 10, suggestedProductFamilyName: "Haferdrink", suggestedProductVariantId: 20, suggestedProductVariantName: "Haferdrink 1 l", assignmentSource: "AI", assignmentStatus: "NEEDS_REVIEW", confidence: 0.72, reason: "LOW_CONFIDENCE", possibleRetroactiveItems: 3 }
];

const productChangePreview: ProductChangePreviewDTO = {
  affectedItemsCount: 1,
  affectedStores: ["REWE"],
  dateFrom: "2026-06-15",
  dateTo: "2026-06-15",
  previousProductFamilyId: 10,
  previousProductFamilyName: "Haferdrink",
  newProductFamilyId: 11,
  newProductFamilyName: "Coca-Cola Zero",
  previousProductVariantId: 20,
  previousProductVariantName: "Haferdrink 1 l",
  newProductVariantId: 21,
  newProductVariantName: "Coca-Cola Zero 0.5 l",
  reportImpact: "Preisreports werden erst in Phase 15c neu berechnet."
};

const receipts: ReceiptDTO[] = [
  {
    id: 1,
    paperlessDocumentId: 1001,
    paperlessDocumentUrl: "http://paperless.example/documents/1001/details",
    importedAt: "2026-06-16T07:30:00Z",
    receiptDate: "2026-06-15",
    receiptTime: "17:42:00",
    storeName: "REWE",
    storeBranch: "Mockstraße 1",
    totalAmount: 42.1,
    currency: "EUR",
    bonusBalance: null,
    bonusPoints: 21,
    bonusType: "REWE Bonus",
    parseStatus: "PARSED",
    parseSource: "RULE",
    parseErrorMessage: null,
    aiParsingSummary: null,
    deletedAt: null,
    deleteReason: null,
    rawText: "REWE Mock Bon",
    items: [
      {
        id: 11,
        receiptId: 1,
        positionIndex: 0,
        description: "Bio Milch",
        quantity: 1,
        unit: "l",
        unitPrice: 1.29,
        totalPrice: 1.29,
        discountAmount: null,
        categoryId: 2,
        categoryName: "Milchprodukte und Eier",
        categorySource: "RULE",
        isManuallyEdited: false,
        aiSuggestion: null,
        productFamilyId: null,
        productFamilyName: null,
        productVariantId: null,
        productVariantName: null,
        productAssignmentSource: null,
        productAssignmentStatus: null,
        productAssignmentConfidence: null,
        computedUnitPrice: null,
        computedUnitPriceUnit: null,
        excludeFromProductPriceComparison: false,
        productPriceExclusionReason: null
      },
      {
        id: 12,
        receiptId: 1,
        positionIndex: 1,
        description: "L CC grat.",
        quantity: null,
        unit: null,
        unitPrice: null,
        totalPrice: -2,
        discountAmount: -2,
        categoryId: 3,
        categoryName: "Pfand und Rabatte",
        categorySource: "RULE",
        isManuallyEdited: false,
        aiSuggestion: null,
        productFamilyId: null,
        productFamilyName: null,
        productVariantId: null,
        productVariantName: null,
        productAssignmentSource: null,
        productAssignmentStatus: "NO_PRODUCT",
        productAssignmentConfidence: null,
        computedUnitPrice: null,
        computedUnitPriceUnit: null,
        excludeFromProductPriceComparison: false,
        productPriceExclusionReason: null
      }
    ]
  },
  {
    id: 2,
    paperlessDocumentId: 1002,
    paperlessDocumentUrl: null,
    importedAt: "2026-06-14T07:30:00Z",
    receiptDate: "2026-06-14",
    receiptTime: "10:15:00",
    storeName: "dm",
    storeBranch: "Mockallee 2",
    totalAmount: 18.75,
    currency: "EUR",
    bonusBalance: null,
    bonusPoints: 9,
    bonusType: "PAYBACK",
    parseStatus: "PARSED",
    parseSource: "AI",
    parseErrorMessage: null,
    aiParsingSummary: {
      lastStatus: "SUCCESS",
      lastTrigger: "MANUAL_REPARSE",
      modelUsed: "openai/gpt-oss-20b",
      overallConfidence: 0.94,
      hasOpenRuleSuggestions: true
    },
    deletedAt: null,
    deleteReason: null,
    rawText: "dm Mock Bon",
    items: []
  }
];

const dashboard: DashboardDTO = {
  currentMonthTotal: 60.85,
  previousMonthTotal: 48.12,
  currentYearTotal: 420.42,
  currentMonthByCategory: [
    { categoryId: 1, categoryName: "Salat, Obst & Gemüse", total: 12.5 },
    { categoryId: 2, categoryName: "Milchprodukte und Eier", total: 21.4 }
  ],
  bonusSummary: [
    { bonusType: "PAYBACK", totalPoints: 9, totalEarnedBalance: null }
  ],
  recentReceipts: receipts,
  uncategorizedItemsCount: 1,
  lastSyncStatus: syncStatus
};

const settings: SettingsDTO = {
  paperlessBaseUrl: "http://paperless:8001",
  paperlessPublicBaseUrl: "http://localhost:8001",
  paperlessDocumentUrlTemplate: "",
  paperlessApiToken: "********",
  paperlessEbonTag: "eBON",
  openRouterApiKey: null,
  openRouterBaseUrl: "https://openrouter.ai/api/v1",
  openRouterModel: "openai/gpt-oss-20b",
  aiCategorizationMinConfidence: 0.9,
  aiParsingFallbackEnabled: true,
  aiParsingModel: "openai/gpt-oss-20b",
  aiParsingMaxTokens: 2500,
  aiParsingTemperature: 0,
  aiParsingMinConfidence: 0.9,
  aiParsingSyncCallLimit: 25,
  aiParsingTextMode: "MINIMIZED",
  aiParsingStoreDebugSnippets: false,
  syncIntervalMinutes: 60,
  currency: "EUR",
  productHistoryMinConfirmedMatches: 3,
  productHistoryMinVariantShare: 0.9
};

const aiParsingLogs = [
  {
    id: 1,
    receiptId: 2,
    trigger: "MANUAL_REPARSE",
    status: "SUCCESS",
    modelUsed: "openai/gpt-oss-20b",
    startedAt: "2026-06-16T08:00:00Z",
    finishedAt: "2026-06-16T08:00:02Z",
    durationMs: 2100,
    overallConfidence: 0.94,
    parseErrorBefore: "total_amount fehlt.",
    failureReason: null,
    fieldConfidence: { receiptDate: 0.98, items: 0.93 },
    warnings: ["storeBranch unsicher"],
    promptSnippet: null,
    responseSnippet: null
  }
];

const parseRuleSuggestions = [
  {
    id: 1,
    receiptId: 2,
    aiParsingLogId: 1,
    storeName: "dm",
    ruleType: "ITEM_PATTERN",
    matchRegex: "^(?<description>.+?)\\s+(?<total>\\d+,\\d{2})\\s+\\d$",
    extractGroup: "description,total",
    confidence: 0.91,
    trigger: "MANUAL_REPARSE",
    problemDescription: "Regelparser erkannte eine dm-Positionszeile nicht.",
    solutionRationale: "Die Regel extrahiert Beschreibung und Betrag aus der Positionszeile.",
    validationStatus: "VALID",
    validationMessage: null,
    status: "OPEN",
    rejectionReason: null,
    acceptedParseRuleId: null,
    receiptContext: {
      receiptId: 2,
      paperlessDocumentId: 1002,
      rawText: "dm\nArtikel SHAMPOO 2,95\nSUMME 2,95",
      parseStatus: "PARSED",
      parseSource: "AI",
      receiptDate: "2026-06-16",
      receiptTime: "08:15:00",
      storeName: "dm",
      storeBranch: "Neuss",
      totalAmount: 2.95,
      currency: "EUR",
      items: [
        {
          positionIndex: 0,
          description: "SHAMPOO",
          quantity: null,
          unit: null,
          unitPrice: null,
          totalPrice: 2.95,
          discountAmount: null
        }
      ]
    }
  }
];

const rules: CategorizationRuleDTO[] = [
  {
    id: 1,
    categoryId: 2,
    categoryName: "Milchprodukte und Eier",
    matchField: "DESCRIPTION",
    matchType: "CONTAINS",
    matchValue: "MILCH",
    priority: 45,
    isActive: true,
    createdAt: "2026-06-16T07:30:00Z"
  }
];

const syncLog: SyncLogDTO[] = [
  {
    id: 1,
    startedAt: "2026-06-16T07:30:00Z",
    finishedAt: "2026-06-16T07:31:00Z",
    status: "SUCCESS",
    newDocumentsCount: 2,
    removedDocumentsCount: 0,
    errorMessage: null
  }
];

const searchResults: SearchResultDTO[] = [
  {
    receiptId: 1,
    receiptItemId: 11,
    receiptDate: "2026-06-15",
    storeName: "REWE",
    description: "Bio Milch",
    totalPrice: 1.29,
    categoryId: 2,
    categoryName: "Milchprodukte und Eier",
    highlights: ["Milch"]
  }
];

const categoryReport: ReportByCategoryDTO[] = [
  { categoryId: 1, categoryName: "Salat, Obst & Gemüse", total: 12.5 },
  { categoryId: 2, categoryName: "Milchprodukte und Eier", total: 21.4 }
];

const periodReport: ReportByPeriodDTO[] = [
  { periodStart: "2026-06-01", period: "2026-06", total: 60.85 }
];

const storeReport: ReportByStoreDTO[] = [
  { storeName: "REWE", total: 42.1, receiptCount: 1 },
  { storeName: "dm", total: 18.75, receiptCount: 1 }
];

const topItemsReport: TopItemReportDTO[] = [
  { description: "Bio Milch", total: 1.29, count: 1 }
];

const bonusReport: BonusReportDTO[] = [
  { bonusType: "PAYBACK", totalPoints: 9, totalEarnedBalance: null }
];
