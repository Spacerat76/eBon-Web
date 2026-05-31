import React from 'react'
import { render, fireEvent, waitFor } from '@testing-library/react'
import CategoryEditor from '../../components/CategoryEditor'

describe('CategoryEditor', () => {
  it('calls onSave and onClose when saving', async () => {
    const onSave = vi.fn(() => Promise.resolve())
    const onClose = vi.fn()
    const { getByLabelText, getByText } = render(<CategoryEditor open={true} onClose={onClose} onSave={onSave} />)

    const name = getByLabelText('Name') as HTMLInputElement
    fireEvent.change(name, { target: { value: 'Food' } })

    fireEvent.click(getByText('Save'))

    await waitFor(() => expect(onSave).toHaveBeenCalled())
    expect(onSave.mock.calls[0][0]).toMatchObject({ name: 'Food' })
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })
})
