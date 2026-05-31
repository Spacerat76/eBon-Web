import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getRules, createRule, updateRule, deleteRule, previewRule } from '../api/endpoints'
import type { CategorizationRule } from '../types/api'

export function useRules() {
  const qc = useQueryClient()

  const query = useQuery<CategorizationRule[]>({
    queryKey: ['rules'],
    queryFn: getRules,
    staleTime: 1000 * 60 * 5,
  })

  const createMut = useMutation({
    mutationFn: (payload: Partial<CategorizationRule>) => createRule(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['rules'] }),
  })

  const updateMut = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: Partial<CategorizationRule> }) => updateRule(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['rules'] }),
  })

  const deleteMut = useMutation({
    mutationFn: (id: number) => deleteRule(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['rules'] }),
  })

  const previewMut = useMutation({ mutationFn: (payload: Partial<CategorizationRule>) => previewRule(payload) })

  return { ...query, createMut, updateMut, deleteMut, previewMut }
}
