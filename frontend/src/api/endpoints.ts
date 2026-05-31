import client from './client'
import type { PaginatedReceipts, Receipt, ReceiptItem, Category, CategorizationRule, ReportsResponse, SyncStatusResponse, SyncLogEntry, Settings, SettingsPayload, TestResult } from '../types/api'

type ReceiptsParams = {
  page?: number
  pageSize?: number
  query?: string
  storeName?: string
  dateFrom?: string
  dateTo?: string
  categoryIds?: number[]
  amountMin?: number
  amountMax?: number
  sort?: string
  order?: 'asc' | 'desc'
}

export async function getReceipts(params: ReceiptsParams = {}) {
  const q = new URLSearchParams()
  if (params.page !== undefined) q.set('page', String(params.page))
  if (params.pageSize !== undefined) q.set('pageSize', String(params.pageSize))
  if (params.query) q.set('q', params.query)
  if (params.storeName) q.set('storeName', params.storeName)
  if (params.dateFrom) q.set('dateFrom', params.dateFrom)
  if (params.dateTo) q.set('dateTo', params.dateTo)
  if (params.categoryIds && params.categoryIds.length) params.categoryIds.forEach(id => q.append('categoryIds', String(id)))
  if (params.amountMin !== undefined) q.set('amountMin', String(params.amountMin))
  if (params.amountMax !== undefined) q.set('amountMax', String(params.amountMax))
  if (params.sort) q.set('sort', params.sort)
  if (params.order) q.set('order', params.order)

  const url = `/receipts?${q.toString()}`
  const res = await client.get(url)
  return res.data as PaginatedReceipts
}

export async function getReceipt(id: number) {
  const res = await client.get(`/receipts/${id}`)
  return res.data as Receipt
}

export async function updateReceipt(id: number, payload: Partial<Receipt>) {
  const res = await client.patch(`/receipts/${id}`, payload)
  return res.data as Receipt
}

export async function updateReceiptItem(itemId: number, payload: Partial<ReceiptItem>) {
  const res = await client.patch(`/receipt-items/${itemId}`, payload)
  return res.data as ReceiptItem
}

export async function reparseReceipt(id: number, force = false) {
  // Accept 409 responses (conflicts) without throwing to let caller handle
  const res = await client.post(`/receipts/${id}/reparse`, { force }, { validateStatus: (s) => s < 500 })
  return { status: res.status, data: res.data }
}

// Categories
export async function getCategories() {
  const res = await client.get('/categories')
  return res.data as Category[]
}

export async function createCategory(payload: Partial<Category>) {
  const res = await client.post('/categories', payload)
  return res.data as Category
}

export async function updateCategory(id: number, payload: Partial<Category>) {
  const res = await client.patch(`/categories/${id}`, payload)
  return res.data as Category
}

export async function deleteCategory(id: number) {
  const res = await client.delete(`/categories/${id}`)
  return res.data
}

// Categorization rules
export async function getRules() {
  const res = await client.get('/categorization-rules')
  return res.data as CategorizationRule[]
}

export async function createRule(payload: Partial<CategorizationRule>) {
  const res = await client.post('/categorization-rules', payload)
  return res.data as CategorizationRule
}

export async function updateRule(id: number, payload: Partial<CategorizationRule>) {
  const res = await client.patch(`/categorization-rules/${id}`, payload)
  return res.data as CategorizationRule
}

export async function deleteRule(id: number) {
  const res = await client.delete(`/categorization-rules/${id}`)
  return res.data
}

export async function previewRule(payload: Partial<CategorizationRule>) {
  const res = await client.post('/categorization-rules/preview', payload)
  return res.data
}

type ReportsParams = {
  dateFrom?: string
  dateTo?: string
  categoryIds?: number[]
}

export async function getReports(params: ReportsParams = {}) {
  const q = new URLSearchParams()
  if (params.dateFrom) q.set('dateFrom', params.dateFrom)
  if (params.dateTo) q.set('dateTo', params.dateTo)
  if (params.categoryIds && params.categoryIds.length) params.categoryIds.forEach(id => q.append('categoryIds', String(id)))

  const url = `/reports?${q.toString()}`
  const res = await client.get(url)
  return res.data as ReportsResponse
}

// Sync endpoints
export async function getSyncStatus() {
  const res = await client.get('/sync/status')
  return res.data as SyncStatusResponse
}

export async function triggerSync() {
  // start sync; allow 202 Accepted or 200
  const res = await client.post('/sync', {}, { validateStatus: (s) => s < 500 })
  return res.data
}

export async function getSyncLog() {
  const res = await client.get('/sync/log')
  return res.data as SyncLogEntry[]
}

// Settings
export async function getSettings() {
  const res = await client.get('/settings')
  return res.data as Settings
}

export async function updateSettings(payload: Partial<SettingsPayload>) {
  const res = await client.patch('/settings', payload)
  return res.data as Settings
}

export async function testPaperless(payload: { baseUrl?: string; apiToken?: string }) {
  const res = await client.post('/settings/test/paperless', payload, { validateStatus: (s) => s < 500 })
  return res.data as TestResult
}

export async function testOpenRouter(payload: { apiKey?: string; model?: string }) {
  const res = await client.post('/settings/test/openrouter', payload, { validateStatus: (s) => s < 500 })
  return res.data as TestResult
}
