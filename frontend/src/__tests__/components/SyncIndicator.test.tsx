import React from 'react'
import { render, screen } from '@testing-library/react'
import * as useSyncModule from '../../hooks/useSync'
import SyncIndicator from '../../components/SyncIndicator'

describe('SyncIndicator', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows spinner when sync is RUNNING', () => {
    vi.spyOn(useSyncModule, 'useSync').mockReturnValue({ statusQuery: { data: { status: 'RUNNING' } } } as any)
    render(<SyncIndicator />)
    expect(screen.getByText(/Syncing/i)).toBeInTheDocument()
  })

  it('shows last run when SUCCESS', () => {
    vi.spyOn(useSyncModule, 'useSync').mockReturnValue({ statusQuery: { data: { status: 'SUCCESS', lastRunAt: '2026-05-31T12:00:00Z' } } } as any)
    render(<SyncIndicator />)
    expect(screen.getByText(/Last sync/i)).toBeInTheDocument()
  })
})
