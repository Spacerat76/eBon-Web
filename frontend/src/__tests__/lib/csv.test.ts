import { exportToCsv } from '../../lib/csv'
import { vi } from 'vitest'
import { waitFor } from '@testing-library/react'

describe('exportToCsv', () => {
  it('creates an anchor and triggers download', async () => {
    const origCreateObjectURL = (URL as any).createObjectURL
    let createdRevoke = false
    if (!(URL as any).createObjectURL) {
      ;(URL as any).createObjectURL = vi.fn(() => 'blob:fake')
    } else {
      vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:fake')
    }
    if (!(URL as any).revokeObjectURL) {
      ;(URL as any).revokeObjectURL = vi.fn()
      createdRevoke = true
    }

    const originalCreateElement = document.createElement.bind(document)
    const anchor = originalCreateElement('a')
    const clickSpy = vi.spyOn(anchor, 'click').mockImplementation(() => {})

    const createElSpy = vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      if (tag === 'a') return anchor as any
      return originalCreateElement(tag)
    })

    exportToCsv('test.csv', [{ a: 1, b: 'x' }])

    expect(clickSpy).toHaveBeenCalled()
    // anchor should be removed shortly after
    await waitFor(() => expect(anchor.parentElement).toBeNull(), { timeout: 500 })

    createElSpy.mockRestore()
    clickSpy.mockRestore()
    if (origCreateObjectURL) {
      ;(URL.createObjectURL as any).mockRestore()
    } else {
      ;(URL as any).createObjectURL = origCreateObjectURL
    }
    if (createdRevoke) {
      ;(URL as any).revokeObjectURL = undefined
    }
  })
})
