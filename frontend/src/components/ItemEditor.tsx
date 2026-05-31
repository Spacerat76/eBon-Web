import React, { useState } from 'react'
import Modal from './Modal'
import { Input } from './ui'
import type { ReceiptItem } from '../types/api'

interface Props {
  open: boolean
  item?: ReceiptItem | null
  onClose: () => void
  onSave: (payload: Partial<ReceiptItem>) => Promise<void>
}

export default function ItemEditor({ open, item, onClose, onSave }: Props) {
  const [form, setForm] = useState<Partial<ReceiptItem>>(item || {})
  const [saving, setSaving] = useState(false)

  React.useEffect(() => setForm(item || {}), [item])

  const handleSave = async () => {
    setSaving(true)
    try {
      await onSave(form)
      onClose()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={item ? 'Edit Item' : 'New Item'}>
      <div className="space-y-3">
        <Input label="Description" value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <div className="grid grid-cols-3 gap-2">
          <Input label="Quantity" value={form.quantity?.toString() || ''} onChange={(e) => setForm({ ...form, quantity: e.target.value ? Number(e.target.value) : undefined })} />
          <Input label="Unit" value={form.unit || ''} onChange={(e) => setForm({ ...form, unit: e.target.value })} />
          <Input label="Unit Price" value={form.unitPrice?.toString() || ''} onChange={(e) => setForm({ ...form, unitPrice: e.target.value ? Number(e.target.value) : undefined })} />
        </div>
        <Input label="Total Price" value={form.totalPrice?.toString() || ''} onChange={(e) => setForm({ ...form, totalPrice: e.target.value ? Number(e.target.value) : undefined })} />
        <div className="flex justify-end space-x-2 mt-2">
          <button className="px-3 py-1 bg-gray-100 rounded" onClick={onClose}>Cancel</button>
          <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={handleSave} disabled={saving}>{saving ? 'Saving...' : 'Save'}</button>
        </div>
      </div>
    </Modal>
  )
}
