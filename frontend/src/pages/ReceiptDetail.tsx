import React, { useState } from 'react'
import { useParams } from 'react-router-dom'
import { Card, Input } from '../components/ui'
import { useReceipt } from '../hooks/useReceipt'
import ItemEditor from '../components/ItemEditor'
import ConflictModal from '../components/ConflictModal'

export default function ReceiptDetail() {
  const { id } = useParams()
  const { data: receipt, isLoading, error, updateReceiptMut, updateItemMut, reparseMut } = useReceipt(id)

  const [editingItem, setEditingItem] = useState<any | null>(null)
  const [conflictData, setConflictData] = useState<any | null>(null)
  const [conflictOpen, setConflictOpen] = useState(false)

  if (isLoading) return <div>Loading...</div>
  if (error) return <div className="text-red-600">Error loading receipt</div>
  if (!receipt) return <div>No receipt</div>

  const handleSaveReceipt = async () => {
    await updateReceiptMut.mutateAsync({
      receiptDate: receipt.receiptDate,
      receiptTime: receipt.receiptTime,
      storeName: receipt.storeName,
      storeBranch: receipt.storeBranch,
      totalAmount: receipt.totalAmount,
    })
  }

  const handleSaveItem = async (payload: any) => {
    if (!editingItem) return
    await updateItemMut.mutateAsync({ itemId: editingItem.id, payload })
  }

  const handleReparse = async (force = false) => {
    const res = await reparseMut.mutateAsync(force)
    // res format: { status, data }
    if (res && res.status === 409) {
      setConflictData(res.data)
      setConflictOpen(true)
    }
  }

  const resolveConflict = async (action: 'overwrite' | 'keep') => {
    setConflictOpen(false)
    if (action === 'overwrite') {
      await reparseMut.mutateAsync(true)
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">Receipt #{receipt.id}</h2>
        <div className="space-x-2">
          <button onClick={() => handleReparse(false)} className="px-3 py-1 bg-gray-100 rounded">Re-Parse</button>
          <button onClick={() => handleReparse(true)} className="px-3 py-1 bg-red-100 rounded">Force Re-Parse</button>
        </div>
      </div>

      <Card>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <Input label="Store" value={receipt.storeName || ''} onChange={(e) => (receipt.storeName = e.target.value)} />
          <Input label="Date" value={receipt.receiptDate || ''} onChange={(e) => (receipt.receiptDate = e.target.value)} />
          <Input label="Time" value={receipt.receiptTime || ''} onChange={(e) => (receipt.receiptTime = e.target.value)} />
        </div>
        <div className="mt-3 grid grid-cols-1 md:grid-cols-2 gap-3">
          <Input label="Total Amount" value={receipt.totalAmount?.toString() || ''} onChange={(e) => (receipt.totalAmount = e.target.value ? Number(e.target.value) : undefined)} />
          <Input label="Currency" value={receipt.currency || 'EUR'} onChange={(e) => (receipt.currency = e.target.value)} />
        </div>
        <div className="mt-3 flex justify-end">
          <button onClick={handleSaveReceipt} className="px-3 py-1 bg-blue-600 text-white rounded">Save</button>
        </div>
      </Card>

      <Card>
        <h3 className="font-medium mb-3">Items</h3>
        <div className="space-y-2">
          {receipt.items && receipt.items.length ? (
            receipt.items.map(item => (
              <div key={item.id} className="flex items-start justify-between bg-gray-50 p-2 rounded">
                <div>
                  <div className="font-medium">{item.description}</div>
                  <div className="text-xs text-gray-500">{item.quantity ?? ''} {item.unit || ''} • {item.totalPrice?.toFixed(2)}</div>
                </div>
                <div className="space-x-2">
                  <button className="px-2 py-0.5 bg-gray-100 rounded" onClick={() => setEditingItem(item)}>Edit</button>
                </div>
              </div>
            ))
          ) : (
            <div className="text-sm text-gray-600">No items</div>
          )}
        </div>
      </Card>

      <Card>
        <h3 className="font-medium mb-2">Raw Text</h3>
        <pre className="whitespace-pre-wrap text-sm bg-gray-50 p-2 rounded">{receipt.rawText}</pre>
      </Card>

      <ItemEditor open={!!editingItem} item={editingItem} onClose={() => setEditingItem(null)} onSave={handleSaveItem} />
      <ConflictModal open={conflictOpen} conflictData={conflictData} onClose={() => setConflictOpen(false)} onResolve={resolveConflict} />
    </div>
  )
}
