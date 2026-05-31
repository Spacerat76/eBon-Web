import React from 'react'
import { Link } from 'react-router-dom'
import Button from '../components/ui/Button'
import SyncIndicator from './SyncIndicator'

export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white shadow">
        <div className="max-w-7xl mx-auto py-4 px-4 sm:px-6 lg:px-8 flex items-center justify-between">
          <h1 className="text-lg font-semibold">eBon Expense Tracker</h1>
          <div className="flex items-center space-x-4">
            <nav className="space-x-4 hidden sm:inline-flex">
              <Link to="/receipts" className="text-sm text-gray-600">Receipts</Link>
              <Link to="/reports" className="text-sm text-gray-600">Reports</Link>
              <Link to="/rules" className="text-sm text-gray-600">Rules</Link>
            </nav>
            <Link to="/settings" className="text-sm text-gray-600">Settings</Link>
              <div className="flex items-center space-x-3">
                <SyncIndicator />
                <Button variant="ghost" onClick={async () => { try { await trigger(); } catch(e) { console.error(e) } }} >Sync</Button>
              </div>
          </div>
        </div>
      </header>
      <main className="max-w-7xl mx-auto py-6 px-4 sm:px-6 lg:px-8">{children}</main>
    </div>
  )
}

function trigger() {
  // Import here to avoid top-level hook usage in header
  // This triggers the sync by calling the endpoint directly
  return fetch((import.meta.env.VITE_API_BASE_URL || '/api') + '/sync', { method: 'POST', headers: { 'Content-Type': 'application/json' } })
}
