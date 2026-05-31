import React, { useEffect, useState } from 'react'
import { Input } from './ui'

interface Props {
  value?: string
  onChange: (v: string) => void
  placeholder?: string
}

export default function SearchBar({ value = '', onChange, placeholder = 'Search receipts' }: Props) {
  const [q, setQ] = useState(value)

  useEffect(() => {
    setQ(value)
  }, [value])

  useEffect(() => {
    const t = setTimeout(() => onChange(q.trim()), 300)
    return () => clearTimeout(t)
  }, [q, onChange])

  return (
    <div className="w-full">
      <Input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder={placeholder}
        aria-label="Search receipts"
      />
    </div>
  )
}
