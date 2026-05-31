import React, { useState } from 'react'
import { useCategories } from '../hooks/useCategories'
import CategoryEditor from '../components/CategoryEditor'
import { Card } from '../components/ui'

export default function Categories() {
  const { data: categories, isLoading, error, createMut, updateMut, deleteMut } = useCategories()
  const [editing, setEditing] = useState<any | null>(null)
  const [open, setOpen] = useState(false)

  const handleSave = async (payload: any) => {
    if (editing) {
      await updateMut.mutateAsync({ id: editing.id, payload })
    } else {
      await createMut.mutateAsync(payload)
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-semibold">Categories</h2>
        <div>
          <button onClick={() => { setEditing(null); setOpen(true) }} className="px-3 py-1 bg-blue-600 text-white rounded">New Category</button>
        </div>
      </div>

      <Card>
        {isLoading && <div>Loading...</div>}
        {error && <div className="text-red-600">Error loading categories</div>}
        <div className="space-y-2">
          {categories?.map(cat => (
            <div key={cat.id} className="flex items-center justify-between p-2 bg-gray-50 rounded">
              <div className="flex items-center space-x-3">
                <div style={{ width: 18, height: 18, background: cat.colorHex || '#ddd' }} className="rounded-sm" />
                <div className="font-medium">{cat.name}</div>
              </div>
              <div className="space-x-2">
                <button className="px-2 py-0.5 bg-gray-100 rounded" onClick={() => { setEditing(cat); setOpen(true) }}>Edit</button>
                <button className="px-2 py-0.5 bg-red-100 rounded" onClick={() => deleteMut.mutate(cat.id)}>Delete</button>
              </div>
            </div>
          ))}
        </div>
      </Card>

      <CategoryEditor open={open} category={editing} onClose={() => setOpen(false)} onSave={handleSave} />
    </div>
  )
}
