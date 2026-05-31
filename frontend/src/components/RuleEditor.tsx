import React, { useState, useEffect } from 'react'
import Modal from './Modal'
import { Input } from './ui'
import type { CategorizationRule } from '../types/api'

interface Props {
  open: boolean
  rule?: CategorizationRule | null
  onClose: () => void
  onSave: (payload: Partial<CategorizationRule>) => Promise<void>
  onPreview?: (payload: Partial<CategorizationRule>) => Promise<any>
}

export default function RuleEditor({ open, rule, onClose, onSave, onPreview }: Props) {
  const [form, setForm] = useState<Partial<CategorizationRule>>(rule || {})
  useEffect(() => setForm(rule || {}), [rule])

  const handleSave = async () => {
    await onSave(form)
    onClose()
  }

  const handlePreview = async () => {
    if (onPreview) await onPreview(form)
  }

  return (
    <Modal open={open} onClose={onClose} title={rule ? 'Edit Rule' : 'New Rule'}>
      <div className="space-y-3">
        <Input label="Match Field" value={form.matchField || ''} onChange={(e) => setForm({ ...form, matchField: e.target.value as any })} />
        <Input label="Match Type" value={form.matchType || ''} onChange={(e) => setForm({ ...form, matchType: e.target.value as any })} />
        <Input label="Match Value" value={form.matchValue || ''} onChange={(e) => setForm({ ...form, matchValue: e.target.value })} />
        <Input label="Priority" value={form.priority?.toString() || '100'} onChange={(e) => setForm({ ...form, priority: Number(e.target.value) })} />
        <div className="flex justify-end space-x-2">
          <button className="px-3 py-1 bg-gray-100 rounded" onClick={onClose}>Cancel</button>
          <button className="px-3 py-1 bg-gray-200 rounded" onClick={handlePreview}>Preview</button>
          <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={handleSave}>Save</button>
        </div>
      </div>
    </Modal>
  )
}
