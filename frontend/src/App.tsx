import React from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout'
import ReceiptsList from './pages/ReceiptsList'
import ReceiptDetail from './pages/ReceiptDetail'
import Reports from './pages/Reports'
import Rules from './pages/Rules'
import Categories from './pages/Categories'
import SyncPage from './pages/Sync'
import Settings from './pages/Settings'

export default function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Navigate to="/receipts" replace />} />
        <Route path="/receipts" element={<ReceiptsList />} />
        <Route path="/receipts/:id" element={<ReceiptDetail />} />
        <Route path="/reports" element={<Reports />} />
        <Route path="/rules" element={<Rules />} />
        <Route path="/categories" element={<Categories />} />
        <Route path="/sync" element={<SyncPage />} />
        <Route path="/settings" element={<Settings />} />
      </Routes>
    </Layout>
  )
}
