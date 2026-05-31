import { getReports } from '../../api/endpoints'
import client from '../../api/client'
import { vi } from 'vitest'

describe('endpoints.getReports', () => {
  it('calls client.get with built query string and returns data', async () => {
    const mock = { byCategory: [], byMonth: [], totalAmount: 0 }
    const spy = vi.spyOn(client, 'get').mockResolvedValue({ data: mock } as any)

    const res = await getReports({ dateFrom: '2026-01-01', categoryIds: [1] })

    expect(spy).toHaveBeenCalled()
    // ensure returned value is correct
    expect(res).toEqual(mock)

    spy.mockRestore()
  })
})
