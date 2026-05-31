export type ParseStatus = 'PENDING' | 'PARSED' | 'PARSE_ERROR' | 'MANUALLY_EDITED'

export interface ReceiptItem {
  id: number
  receiptId: number
  positionIndex: number
  description: string
  quantity?: number
  unit?: string
  unitPrice?: number
  totalPrice: number
  discountAmount?: number
  categoryId?: number | null
  categorySource?: 'RULE' | 'AI' | 'MANUAL' | null
  isManuallyEdited: boolean
}

export interface Receipt {
  id: number
  paperlessDocumentId: number
  importedAt: string
  receiptDate?: string
  receiptTime?: string
  storeName?: string
  storeBranch?: string
  totalAmount?: number
  currency?: string
  rawText: string
  parseStatus: ParseStatus
  parseErrorMessage?: string
  items?: ReceiptItem[]
}

export interface Category {
  id: number
  name: string
  colorHex?: string | null
  icon?: string | null
  isActive: boolean
}

export interface CategorizationRule {
  id: number
  categoryId: number
  matchField: 'DESCRIPTION' | 'STORE_NAME'
  matchType: 'CONTAINS' | 'STARTS_WITH' | 'ENDS_WITH' | 'EXACT' | 'REGEX'
  matchValue: string
  priority: number
  isActive: boolean
}

export interface Paginated<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export type PaginatedReceipts = Paginated<Receipt>

export interface ReportByCategory {
  categoryId: number
  categoryName: string
  totalAmount: number
}

export interface ReportByMonth {
  month: string
  totalAmount: number
}

export interface ReportsResponse {
  byCategory: ReportByCategory[]
  byMonth: ReportByMonth[]
  totalAmount: number
}

export type SyncState = 'IDLE' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface SyncStatusResponse {
  status: SyncState
  lastRunAt?: string
  lastMessage?: string | null
}

export interface SyncLogEntry {
  timestamp: string
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'
  message: string
}

export interface Settings {
  paperlessBaseUrl?: string | null
  paperlessApiTokenSet?: boolean
  openRouterModel?: string | null
  openRouterApiKeySet?: boolean
  syncIntervalMinutes?: number | null
}

export interface SettingsPayload {
  paperlessBaseUrl?: string | null
  paperlessApiToken?: string | null
  openRouterModel?: string | null
  openRouterApiKey?: string | null
  syncIntervalMinutes?: number | null
}

export interface TestResult {
  ok: boolean
  message?: string
}
