import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getCategories, createCategory, updateCategory, deleteCategory } from '../api/endpoints'
import type { Category } from '../types/api'

export function useCategories() {
  const qc = useQueryClient()

  const query = useQuery<Category[]>({
    queryKey: ['categories'],
    queryFn: getCategories,
    staleTime: 1000 * 60 * 5,
  })

  const createMut = useMutation({
    mutationFn: (payload: Partial<Category>) => createCategory(payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
  })

  const updateMut = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: Partial<Category> }) => updateCategory(id, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
  })

  const deleteMut = useMutation({
    mutationFn: (id: number) => deleteCategory(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['categories'] }),
  })

  return { ...query, createMut, updateMut, deleteMut }
}
