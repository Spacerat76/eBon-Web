import React, { useState } from 'react'
import { useRules } from '../hooks/useRules'
import RuleEditor from '../components/RuleEditor'
import { Card } from '../components/ui'

export default function Rules() {
  const { data: rules, isLoading, error, createMut, updateMut, deleteMut, previewMut } = useRules()
  const [editing, setEditing] = useState<any | null>(null)
  const [open, setOpen] = useState(false)

  const handleSave = async (payload: any) => {
    if (editing) {
      await updateMut.mutateAsync({ id: editing.id, payload })
    } else {
      await createMut.mutateAsync(payload)
    }
  }

  const handlePreview = async (payload: any) => {
    const res = await previewMut.mutateAsync(payload)
    // show preview result - for now use alert
    alert(JSON.stringify(res, null, 2))
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-semibold">Categorization Rules</h2>
        <div>
          <button onClick={() => { setEditing(null); setOpen(true) }} className="px-3 py-1 bg-blue-600 text-white rounded">New Rule</button>
        </div>
      </div>

      <Card>
        {isLoading && <div>Loading...</div>}
        {error && <div className="text-red-600">Error loading rules</div>}
        <div className="space-y-2">
          {rules?.map(r => (
            <div key={r.id} className="flex items-center justify-between p-2 bg-gray-50 rounded">
              <div>
                <div className="font-medium">{r.matchField} {r.matchType} "{r.matchValue}" → Category {r.categoryId}</div>
                <div className="text-xs text-gray-500">Priority: {r.priority}</div>
              </div>
              <div className="space-x-2">
                <button className="px-2 py-0.5 bg-gray-100 rounded" onClick={() => { setEditing(r); setOpen(true) }}>Edit</button>
                <button className="px-2 py-0.5 bg-red-100 rounded" onClick={() => deleteMut.mutate(r.id)}>Delete</button>
              </div>
            </div>
          ))}
        </div>
      </Card>

      <RuleEditor open={open} rule={editing} onClose={() => setOpen(false)} onSave={handleSave} onPreview={handlePreview} />
    </div>
  )
}
