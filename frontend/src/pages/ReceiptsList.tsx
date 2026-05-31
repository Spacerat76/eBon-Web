import React, { useState } from 'react'
import SearchBar from '../components/SearchBar'
import ReceiptCard from '../components/ReceiptCard'
import Pagination from '../components/Pagination'
import { useReceipts } from '../hooks/useReceipts'
import { Card } from '../components/ui'

export default function ReceiptsList() {
  const [page, setPage] = useState(1)
  const [pageSize] = useState(20)
  const [query, setQuery] = useState('')

  const { data, isLoading, error } = useReceipts({ page, pageSize, query })

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-semibold">Receipts</h2>
      </div>

      <Card className="mb-4">
        <div className="grid grid-cols-1 gap-2 md:grid-cols-3">
          <div className="md:col-span-2">
            <SearchBar value={query} onChange={(v) => { setQuery(v); setPage(1) }} />
          </div>
        </div>
      </Card>

      {isLoading && <div>Loading...</div>}
      {error && <div className="text-red-600">Error loading receipts</div>}

      <div className="space-y-3">
        {data?.items?.length ? (
          data.items.map(r => (
            <ReceiptCard key={r.id} receipt={r} />
          ))
        ) : (
          <div className="text-sm text-gray-600">No receipts found</div>
        )}
      </div>

      {data && (
        <Pagination
          page={data.page}
          pageSize={data.pageSize}
          total={data.total}
          onPageChange={(p) => setPage(p)}
        />
      )}
    </div>
  )
}
