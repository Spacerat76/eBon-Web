import React from 'react'
import Modal from './Modal'

interface Props {
  open: boolean
  conflictData: any
  onClose: () => void
  onResolve: (action: 'overwrite' | 'keep') => void
}

export default function ConflictModal({ open, conflictData, onClose, onResolve }: Props) {
  return (
    <Modal open={open} onClose={onClose} title="Re-Parse Conflict">
      <div className="space-y-3">
        <p className="text-sm text-gray-700">The re-parse found conflicts with manually edited items. Preview raw conflict data below.</p>
        <pre className="max-h-60 overflow-auto bg-gray-50 p-2 rounded text-xs">{JSON.stringify(conflictData, null, 2)}</pre>
        <div className="flex justify-end space-x-2">
          <button className="px-3 py-1 bg-gray-100 rounded" onClick={() => onResolve('keep')}>Keep manual</button>
          <button className="px-3 py-1 bg-red-600 text-white rounded" onClick={() => onResolve('overwrite')}>Overwrite with parsed</button>
        </div>
      </div>
    </Modal>
  )
}
