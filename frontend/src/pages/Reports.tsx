import React, { useState } from 'react'
import { useReports } from '../hooks/useReports'
import { useCategories } from '../hooks/useCategories'
import { exportToCsv } from '../lib/csv'
import { Card } from '../components/ui'
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  PieChart,
  Pie,
} from 'recharts'
import { format } from 'date-fns'

export default function Reports() {
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [categoryId, setCategoryId] = useState<number | ''>('')

  const params = {
    dateFrom: dateFrom || undefined,
    dateTo: dateTo || undefined,
    categoryIds: categoryId ? [categoryId] : undefined,
  }

  const { data, isLoading, error, refetch } = useReports(params)
  const { data: categories } = useCategories()

  const handleExport = (which: 'byCategory' | 'byMonth') => {
    const rows = (data?.[which] || []).map((item: any) => {
      if (which === 'byCategory') {
        return {
          categoryId: item.categoryId,
          categoryName: item.categoryName,
          totalAmount: item.totalAmount,
        }
      }
      return {
        month: item.month,
        totalAmount: item.totalAmount,
      }
    })
    const filename = `ebon-report-${which}-${format(new Date(), 'yyyyMMdd')}.csv`
    exportToCsv(filename, rows)
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-semibold">Reports</h2>
        <div className="space-x-2">
          <button onClick={() => refetch()} className="px-3 py-1 bg-gray-100 rounded">Refresh</button>
        </div>
      </div>

      <Card>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 mb-4">
          <label className="block">
            <div className="text-xs text-gray-600">From</div>
            <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className="mt-1 block w-full" />
          </label>
          <label className="block">
            <div className="text-xs text-gray-600">To</div>
            <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className="mt-1 block w-full" />
          </label>
          <label className="block">
            <div className="text-xs text-gray-600">Category</div>
            <select value={categoryId} onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : '')} className="mt-1 block w-full">
              <option value=''>All</option>
              {categories?.map(c => (<option key={c.id} value={c.id}>{c.name}</option>))}
            </select>
          </label>
          <div className="flex items-end gap-2">
            <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={() => refetch()}>Apply</button>
            <button className="px-3 py-1 bg-green-600 text-white rounded" onClick={() => handleExport('byCategory')}>Export Categories CSV</button>
            <button className="px-3 py-1 bg-green-600 text-white rounded" onClick={() => handleExport('byMonth')}>Export Months CSV</button>
          </div>
        </div>

        {isLoading && <div>Loading...</div>}
        {error && <div className="text-red-600">Error loading reports</div>}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="p-2 bg-white rounded shadow">
            <h3 className="text-sm font-medium mb-2">Totals by Category</h3>
            {data?.byCategory && data.byCategory.length ? (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={data.byCategory.map((b: any) => ({ name: b.categoryName, total: b.totalAmount }))}>
                  <XAxis dataKey="name" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="total" fill="#4f46e5" />
                </BarChart>
              </ResponsiveContainer>
            ) : <div className="text-sm text-gray-500">No data</div>}
          </div>

          <div className="p-2 bg-white rounded shadow">
            <h3 className="text-sm font-medium mb-2">Totals by Month</h3>
            {data?.byMonth && data.byMonth.length ? (
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie dataKey="totalAmount" data={data.byMonth} nameKey="month" cx="50%" cy="50%" outerRadius={80} fill="#06b6d4" label />
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            ) : <div className="text-sm text-gray-500">No data</div>}
          </div>
        </div>
      </Card>
    </div>
  )
}
