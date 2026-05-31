import React from 'react'
import { render, fireEvent } from '@testing-library/react'
import Modal from '../../components/Modal'

describe('Modal', () => {
  it('renders children when open and calls onClose when closed', () => {
    const onClose = vi.fn()
    const { getByText, queryByText } = render(<Modal open={false} onClose={onClose} title="T">Hello</Modal>)
    expect(queryByText('Hello')).toBeNull()

    const { getByText: getByText2 } = render(<Modal open={true} onClose={onClose} title="T">Hello</Modal>)
    expect(getByText2('Hello')).toBeInTheDocument()

    const closeBtn = getByText2('✕')
    fireEvent.click(closeBtn)
    expect(onClose).toHaveBeenCalled()
  })
})
