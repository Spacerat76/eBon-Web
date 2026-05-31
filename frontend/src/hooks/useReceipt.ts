import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getReceipt, updateReceipt, updateReceiptItem, reparseReceipt } from '../api/endpoints'
import type { Receipt, ReceiptItem } from '../types/api'

export function useReceipt(id?: string | number) {
  const queryClient = useQueryClient()
  const receiptId = id ? Number(id) : undefined

  const query = useQuery<Receipt>({
    queryKey: ['receipt', receiptId],
    queryFn: () => getReceipt(receiptId as number),
    enabled: !!receiptId,
  })

  const updateReceiptMut = useMutation({
    mutationFn: (payload: Partial<Receipt>) => updateReceipt(receiptId as number, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['receipt', receiptId] })
      queryClient.invalidateQueries({ queryKey: ['receipts'] })
    }
  })

  const updateItemMut = useMutation({
    mutationFn: ({ itemId, payload }: { itemId: number, payload: Partial<ReceiptItem> }) => updateReceiptItem(itemId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['receipt', receiptId] })
  })

  const reparseMut = useMutation({
    mutationFn: (force: boolean) => reparseReceipt(receiptId as number, force),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['receipt', receiptId] })
  })

  return {
    ...query,
    updateReceiptMut,
    updateItemMut,
    reparseMut,
  }
}
