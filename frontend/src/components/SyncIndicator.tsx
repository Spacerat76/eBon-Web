import React from 'react'
import { useSync } from '../hooks/useSync'

function Spinner({ size = 16 }: { size?: number }) {
  return (
    <svg className="animate-spin" width={size} height={size} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <circle cx="12" cy="12" r="10" stroke="#cbd5e1" strokeWidth="4" />
      <path d="M22 12a10 10 0 00-10-10" stroke="#2563eb" strokeWidth="4" strokeLinecap="round" />
    </svg>
  )
}

export default function SyncIndicator() {
  const { statusQuery } = useSync()
  const status = statusQuery.data?.status || 'IDLE'
  const lastRunAt = statusQuery.data?.lastRunAt

  return (
    <div className="flex items-center space-x-2 text-sm text-gray-600">
      {status === 'RUNNING' ? (
        <div className="flex items-center space-x-2">
          <Spinner />
          <span>Syncing…</span>
        </div>
      ) : (
        <div className="flex items-center space-x-2">
          <svg width="10" height="10" viewBox="0 0 10 10" className="rounded-full" fill={status === 'SUCCESS' ? '#10b981' : status === 'FAILED' ? '#ef4444' : '#94a3b8'}>
            <circle cx="5" cy="5" r="5" />
          </svg>
          <span>{status === 'IDLE' ? 'Idle' : status === 'SUCCESS' ? 'Last sync' : status}</span>
          {lastRunAt && <span className="text-xs text-gray-400">{new Date(lastRunAt).toLocaleString()}</span>}
        </div>
      )}
    </div>
  )
}
