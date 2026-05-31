import React from 'react'
import { render } from '@testing-library/react'
import ReceiptCard from '../../components/ReceiptCard'
import { MemoryRouter } from 'react-router-dom'

describe('ReceiptCard', () => {
  it('renders receipt info', () => {
    const receipt: any = {
      id: 1,
      storeName: 'Shop',
      receiptDate: '2026-05-31',
      totalAmount: 12.5,
      parseStatus: 'PARSED',
      items: [{ description: 'Item A' }]
    }
    const { getByText } = render(
      <MemoryRouter>
        <ReceiptCard receipt={receipt} />
      </MemoryRouter>
    )

    expect(getByText('Shop')).toBeInTheDocument()
    expect(getByText('12.50')).toBeInTheDocument()
    expect(getByText('PARSED')).toBeInTheDocument()
  })
})
