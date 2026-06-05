export type ParseStatus = "PENDING" | "PARSED" | "PARSE_ERROR" | "MANUALLY_EDITED";

export type SyncStatus = "SUCCESS" | "FAILED" | "RUNNING";

export type CategorySource = "RULE" | "AI" | "MANUAL";

export type DeleteReason = "USER_DELETED" | "TAG_REMOVED";

export type AiCategorizationRejectionReason =
  | "LOW_CONFIDENCE"
  | "UNKNOWN_CATEGORY"
  | "INVALID_RESPONSE";

export interface ApiErrorResponse {
  status: number;
  error: string;
  message: string;
  timestamp: string;
  path: string;
  traceId?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  sortBy?: string;
  sortDir?: "asc" | "desc";
}

export interface SyncStatusDTO {
  lastSyncAt: string | null;
  lastSyncStatus: SyncStatus | null;
  newDocumentsCount: number;
  removedDocumentsCount: number;
  errorCount: number;
  isSyncing: boolean;
}

export interface SyncLogDTO {
  id: number;
  startedAt: string;
  finishedAt: string | null;
  status: SyncStatus;
  newDocumentsCount: number;
  removedDocumentsCount: number;
  errorMessage: string | null;
}

export interface AiSuggestionDTO {
  categoryId: number | null;
  categoryName: string | null;
  confidence: number | null;
  rejectionReason: AiCategorizationRejectionReason | null;
}

export interface ReceiptItemDTO {
  id: number;
  receiptId: number;
  positionIndex: number;
  description: string;
  quantity: number | null;
  unit: string | null;
  unitPrice: number | null;
  totalPrice: number;
  discountAmount: number | null;
  categoryId: number | null;
  categoryName: string | null;
  categorySource: CategorySource | null;
  isManuallyEdited: boolean;
  aiSuggestion: AiSuggestionDTO | null;
}

export interface ReceiptDTO {
  id: number;
  paperlessDocumentId: number | null;
  paperlessDocumentUrl: string | null;
  importedAt: string;
  receiptDate: string | null;
  receiptTime: string | null;
  storeName: string | null;
  storeBranch: string | null;
  totalAmount: number | null;
  currency: string;
  bonusBalance: number | null;
  bonusPoints: number | null;
  bonusType: string | null;
  parseStatus: ParseStatus;
  parseErrorMessage: string | null;
  deletedAt: string | null;
  deleteReason: DeleteReason | null;
  rawText: string | null;
  items: ReceiptItemDTO[];
}

export interface CategoryDTO {
  id: number;
  name: string;
  colorHex: string;
  icon: string | null;
  isActive: boolean;
  sortOrder: number;
  assignedItemsCount: number;
}

export interface ReportByCategoryDTO {
  categoryId: number | null;
  categoryName: string;
  total: number;
}

export interface BonusReportDTO {
  bonusType: string;
  totalPoints: number | null;
  totalEarnedBalance: number | null;
}

export interface DashboardDTO {
  currentMonthTotal: number;
  previousMonthTotal: number;
  currentYearTotal: number;
  currentMonthByCategory: ReportByCategoryDTO[];
  bonusSummary: BonusReportDTO[];
  recentReceipts: ReceiptDTO[];
  uncategorizedItemsCount: number;
  lastSyncStatus: SyncStatusDTO;
}

export interface SettingsDTO {
  paperlessBaseUrl: string | null;
  paperlessPublicBaseUrl: string | null;
  paperlessDocumentUrlTemplate: string | null;
  paperlessApiToken: string | null;
  paperlessEbonTag: string | null;
  openRouterApiKey: string | null;
  openRouterBaseUrl: string | null;
  openRouterModel: string | null;
  aiCategorizationMinConfidence: number | null;
  syncIntervalMinutes: number | null;
  currency: string | null;
}

export interface SettingsConnectionTestResponse {
  target: "PAPERLESS" | "OPENROUTER";
  success: boolean;
  message: string;
}

export interface SearchResultDTO {
  receiptId: number;
  receiptItemId: number;
  receiptDate: string | null;
  storeName: string | null;
  description: string;
  totalPrice: number;
  categoryId: number | null;
  categoryName: string | null;
  highlights: string[];
}

export interface SearchParams {
  q?: string;
  store?: string;
  dateFrom?: string;
  dateTo?: string;
  categoryIds?: number[];
  uncategorizedOnly?: boolean;
  amountMin?: number | null;
  amountMax?: number | null;
  page?: number;
  size?: number;
  sortBy?: "receiptDate" | "storeName" | "description" | "totalPrice";
  sortDir?: "asc" | "desc";
}

export interface ReportFilters {
  dateFrom?: string;
  dateTo?: string;
  categoryIds?: number[];
  store?: string;
  groupBy?: "day" | "week" | "month" | "year";
  size?: number;
}

export interface ReportByPeriodDTO {
  periodStart: string;
  period: string;
  total: number;
}

export interface ReportByStoreDTO {
  storeName: string;
  total: number;
  receiptCount: number;
}

export interface TopItemReportDTO {
  description: string;
  total: number;
  count: number;
}

export type RuleMatchField = "DESCRIPTION" | "STORE_NAME";
export type RuleMatchType = "CONTAINS" | "STARTS_WITH" | "ENDS_WITH" | "EXACT" | "REGEX";

export interface CategorizationRuleDTO {
  id: number;
  categoryId: number;
  categoryName: string;
  matchField: RuleMatchField;
  matchType: RuleMatchType;
  matchValue: string;
  priority: number;
  isActive: boolean;
  createdAt: string;
}

export interface CategorizationRuleRequest {
  categoryId: number;
  matchField: RuleMatchField;
  matchType: RuleMatchType;
  matchValue: string;
  priority?: number | null;
  isActive?: boolean | null;
  applyToExisting?: boolean | null;
}

export interface CategorizationRulePreviewRequest {
  categoryId?: number | null;
  matchField: RuleMatchField;
  matchType: RuleMatchType;
  matchValue: string;
}

export interface CategorizationRulePreviewResponse {
  matchingItemsCount: number;
}

export interface CategorizationRuleApplyResponse {
  changedItemsCount: number;
}

export interface CategoryRequest {
  name: string;
  colorHex: string | null;
  icon: string | null;
  sortOrder: number | null;
  isActive: boolean | null;
}

export interface CategoryPatchRequest {
  name?: string | null;
  colorHex?: string | null;
  icon?: string | null;
  sortOrder?: number | null;
  isActive?: boolean | null;
}

export interface DataMaintenanceResultDTO {
  message: string;
  totalReceipts: number;
  processedReceipts: number;
  skippedManualReceipts: number;
  deletedReceipts: number;
  deletedSyncLogs: number;
}

export interface BackupTableValidationDTO {
  name: string;
  recordCount: number;
  valid: boolean;
}

export interface BackupValidationReportDTO {
  valid: boolean;
  manifestVersion: string | null;
  tables: BackupTableValidationDTO[];
  warnings: string[];
  errors: string[];
}

export interface BackupRestoreResultDTO {
  message: string;
  validation: BackupValidationReportDTO;
}

export interface MessageResponse {
  message: string;
}

export interface ReceiptListParams {
  page?: number;
  size?: number;
  sortBy?: "receiptDate" | "importedAt" | "storeName" | "totalAmount" | "parseStatus";
  sortDir?: "asc" | "desc";
  status?: ParseStatus | "";
  dateFrom?: string;
  dateTo?: string;
  store?: string;
  includeDeleted?: boolean;
  uncategorizedOnly?: boolean;
}

export interface ReceiptItemUpdateRequest {
  id?: number;
  positionIndex?: number;
  description?: string;
  quantity?: number | null;
  unit?: string | null;
  unitPrice?: number | null;
  totalPrice?: number | null;
  discountAmount?: number | null;
  categoryId?: number | null;
  categorySource?: CategorySource | null;
}

export interface ReceiptItemCreateRequest {
  positionIndex?: number;
  description: string;
  quantity?: number | null;
  unit?: string | null;
  unitPrice?: number | null;
  totalPrice: number;
  discountAmount?: number | null;
  categoryId?: number | null;
  categorySource?: CategorySource | null;
}

export interface ReceiptUpdateRequest {
  receiptDate: string | null;
  receiptTime: string | null;
  storeName: string | null;
  storeBranch: string | null;
  totalAmount: number | null;
  currency: string | null;
  bonusBalance: number | null;
  bonusPoints: number | null;
  bonusType: string | null;
  items?: ReceiptItemUpdateRequest[];
}
