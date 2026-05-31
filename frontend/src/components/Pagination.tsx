import React from 'react'

interface Props {
  page: number
  pageSize: number
  total: number
  onPageChange: (p: number) => void
}

export default function Pagination({ page, pageSize, total, onPageChange }: Props) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  return (
    <div className="flex items-center justify-between mt-4">
      <div className="text-sm text-gray-600">Page {page} of {totalPages} — {total} items</div>
      <div className="flex items-center space-x-2">
        <button
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
          className="px-2 py-1 bg-gray-100 rounded disabled:opacity-50"
        >Prev</button>
        <button
          disabled={page >= totalPages}
          onClick={() => onPageChange(page + 1)}
          className="px-2 py-1 bg-gray-100 rounded disabled:opacity-50"
        >Next</button>
      </div>
    </div>
  )
}
