import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getSyncStatus, triggerSync, getSyncLog } from '../api/endpoints'
import type { SyncStatusResponse, SyncLogEntry } from '../types/api'

export function useSync() {
  const qc = useQueryClient()

  const statusQuery = useQuery<SyncStatusResponse>({
    queryKey: ['sync', 'status'],
    queryFn: getSyncStatus,
    // Poll faster when running
    refetchInterval: (data) => (data?.status === 'RUNNING' ? 2000 : false),
    staleTime: 1000 * 5,
  })

  const logsQuery = useQuery<SyncLogEntry[]>({
    queryKey: ['sync', 'logs'],
    queryFn: getSyncLog,
    enabled: !!statusQuery.data,
    refetchInterval: () => (statusQuery.data?.status === 'RUNNING' ? 2000 : false),
    staleTime: 1000 * 5,
  })

  const triggerMut = useMutation({
    mutationFn: () => triggerSync(),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sync'] })
    },
  })

  return { statusQuery, logsQuery, triggerMut }
}
