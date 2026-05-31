import React from 'react'
import { cn } from '../../lib/cn'

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string | null
}

export default function Input({ label, error, className, ...props }: InputProps) {
  return (
    <label className="block">
      {label && <span className="text-sm font-medium text-gray-700">{label}</span>}
      <input
        {...props}
        className={cn(
          'mt-1 block w-full rounded-md border-gray-300 shadow-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500',
          className
        )}
      />
      {error && <p className="text-sm text-red-600 mt-1">{error}</p>}
    </label>
  )
}
