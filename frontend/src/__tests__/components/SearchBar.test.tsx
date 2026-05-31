import React from 'react'
import { render, fireEvent } from '@testing-library/react'
import SearchBar from '../../components/SearchBar'
import { vi } from 'vitest'

describe('SearchBar', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('debounces calls to onChange', async () => {
    vi.useFakeTimers()
    const onChange = vi.fn()
    const { getByLabelText } = render(<SearchBar value="" onChange={onChange} />)
    const input = getByLabelText('Search receipts') as HTMLInputElement

    fireEvent.change(input, { target: { value: 'abc' } })

    // not called immediately
    expect(onChange).not.toHaveBeenCalled()

    // advance timers past debounce
    vi.advanceTimersByTime(300)

    expect(onChange).toHaveBeenCalledWith('abc')
  })
})
