import React, { useState, useEffect } from 'react'
import Modal from './Modal'
import { Input } from './ui'
import type { Category } from '../types/api'

interface Props {
  open: boolean
  category?: Category | null
  onClose: () => void
  onSave: (payload: Partial<Category>) => Promise<void>
}

export default function CategoryEditor({ open, category, onClose, onSave }: Props) {
  const [form, setForm] = useState<Partial<Category>>(category || {})
  useEffect(() => setForm(category || {}), [category])

  const handleSave = async () => {
    await onSave(form)
    onClose()
  }

  return (
    <Modal open={open} onClose={onClose} title={category ? 'Edit Category' : 'New Category'}>
      <div className="space-y-3">
        <Input label="Name" value={form.name || ''} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <Input label="Color (hex)" value={form.colorHex || ''} onChange={(e) => setForm({ ...form, colorHex: e.target.value })} />
        <Input label="Icon" value={form.icon || ''} onChange={(e) => setForm({ ...form, icon: e.target.value })} />
        <div className="flex justify-end space-x-2">
          <button className="px-3 py-1 bg-gray-100 rounded" onClick={onClose}>Cancel</button>
          <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={handleSave}>Save</button>
        </div>
      </div>
    </Modal>
  )
}
