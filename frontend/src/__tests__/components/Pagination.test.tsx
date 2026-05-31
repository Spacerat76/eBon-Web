import React from 'react'
import { render, fireEvent } from '@testing-library/react'
import Pagination from '../../components/Pagination'

describe('Pagination', () => {
  it('disables prev on first page and next on last page, triggers callbacks', () => {
    const onPageChange = vi.fn()
    const { getByText, rerender } = render(<Pagination page={1} pageSize={10} total={30} onPageChange={onPageChange} />)

    const prev = getByText('Prev') as HTMLButtonElement
    const next = getByText('Next') as HTMLButtonElement

    expect(prev).toBeDisabled()
    expect(next).not.toBeDisabled()

    // go to page 2
    rerender(<Pagination page={2} pageSize={10} total={30} onPageChange={onPageChange} />)
    expect(prev).not.toBeDisabled()

    fireEvent.click(next)
    expect(onPageChange).toHaveBeenCalledWith(3)

    fireEvent.click(prev)
    expect(onPageChange).toHaveBeenCalledWith(1)
  })
})
