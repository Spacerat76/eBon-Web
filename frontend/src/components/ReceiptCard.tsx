import React from 'react'
import { Link } from 'react-router-dom'
import type { Receipt } from '../types/api'

export default function ReceiptCard({ receipt }: { receipt: Receipt }) {
  return (
    <div className="bg-white shadow rounded-md p-4 flex justify-between items-start">
      <div>
        <Link to={`/receipts/${receipt.id}`} className="text-sm font-medium text-blue-600">
          {receipt.storeName || 'Unknown Store'}
        </Link>
        <div className="text-xs text-gray-500">{receipt.receiptDate || receipt.importedAt}</div>
        <div className="mt-2 text-sm text-gray-700">{receipt.items && receipt.items.length > 0 ? receipt.items[0].description : ''}</div>
      </div>
      <div className="text-right">
        <div className="text-sm font-semibold">{receipt.totalAmount?.toFixed(2) ?? '-'}</div>
        <div className="text-xs mt-1">
          <span className="px-2 py-0.5 rounded-full bg-gray-100 text-gray-700 text-xs">{receipt.parseStatus}</span>
        </div>
      </div>
    </div>
  )
}
