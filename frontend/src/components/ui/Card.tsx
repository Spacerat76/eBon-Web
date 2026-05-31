import React from 'react'

export default function Card({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={`bg-white shadow-sm rounded-md p-4 ${className || ''}`}>{children}</div>
}
