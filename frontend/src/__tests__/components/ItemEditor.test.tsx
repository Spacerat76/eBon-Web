import React from 'react'
import { render, fireEvent, waitFor } from '@testing-library/react'
import ItemEditor from '../../components/ItemEditor'

describe('ItemEditor', () => {
  it('shows saving state while onSave promise is pending and calls onClose after', async () => {
    let resolveSave: any
    const savePromise = new Promise<void>((res) => { resolveSave = res })
    const onSave = vi.fn(() => savePromise)
    const onClose = vi.fn()

    const { getByText } = render(<ItemEditor open={true} onClose={onClose} onSave={onSave} />)

    const saveBtn = getByText('Save')
    fireEvent.click(saveBtn)

    // button should show saving state
    await waitFor(() => expect(getByText('Saving...')).toBeInTheDocument())

    // resolve save
    resolveSave()

    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })
})
