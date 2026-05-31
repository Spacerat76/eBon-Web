import React from 'react'

export default function Modal({ open, title, onClose, children }: { open: boolean; title?: string; onClose: () => void; children: React.ReactNode }) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-white rounded-md shadow-lg max-w-2xl w-full p-4 z-10">
        {title && <div className="text-lg font-semibold mb-2">{title}</div>}
        <div>{children}</div>
        <button className="absolute top-2 right-2 text-gray-500" onClick={onClose}>✕</button>
      </div>
    </div>
  )
}
