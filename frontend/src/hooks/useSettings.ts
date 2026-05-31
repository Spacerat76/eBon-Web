import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getSettings, updateSettings, testPaperless, testOpenRouter } from '../api/endpoints'
import type { Settings, SettingsPayload, TestResult } from '../types/api'

export function useSettings() {
  const qc = useQueryClient()

  const query = useQuery<Settings>({
    queryKey: ['settings'],
    queryFn: getSettings,
    staleTime: 1000 * 60 * 5,
  })

  const updateMut = useMutation({
    mutationFn: (payload: Partial<SettingsPayload>) => updateSettings(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['settings'] }),
  })

  const testPaperlessMut = useMutation({ mutationFn: (payload: { baseUrl?: string; apiToken?: string }) => testPaperless(payload) })
  const testOpenRouterMut = useMutation({ mutationFn: (payload: { apiKey?: string; model?: string }) => testOpenRouter(payload) })

  return { ...query, updateMut, testPaperlessMut, testOpenRouterMut }
}
