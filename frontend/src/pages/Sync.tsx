import React from 'react'
import { useSync } from '../hooks/useSync'
import Card from '../components/ui/Card'

export default function Sync() {
  const { statusQuery, logsQuery, triggerMut } = useSync()

  const status = statusQuery.data?.status || 'IDLE'
  const lastRun = statusQuery.data?.lastRunAt

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-semibold">Sync</h2>
        <div className="space-x-2">
          <button className="px-3 py-1 bg-blue-600 text-white rounded" onClick={() => triggerMut.mutateAsync()}>Start Sync</button>
        </div>
      </div>

      <Card>
        <div className="mb-4">
          <div className="text-sm text-gray-700">Status: <strong>{status}</strong></div>
          {lastRun && <div className="text-xs text-gray-500">Last run: {new Date(lastRun).toLocaleString()}</div>}
        </div>

        <div>
          <h3 className="text-sm font-medium mb-2">Recent Log</h3>
          <div className="space-y-2 max-h-64 overflow-auto">
            {logsQuery.isLoading && <div>Loading log…</div>}
            {logsQuery.data && logsQuery.data.length === 0 && <div className="text-sm text-gray-500">No log entries</div>}
            {logsQuery.data?.map((l) => (
              <div key={l.timestamp} className="p-2 bg-gray-50 rounded">
                <div className="text-xs text-gray-400">{new Date(l.timestamp).toLocaleString()} • {l.level}</div>
                <div className="text-sm">{l.message}</div>
              </div>
            ))}
          </div>
        </div>
      </Card>
    </div>
  )
}
