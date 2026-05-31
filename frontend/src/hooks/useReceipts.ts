import { useQuery } from '@tanstack/react-query'
import { getReceipts } from '../api/endpoints'
import type { PaginatedReceipts } from '../types/api'

type UseReceiptsParams = {
  page?: number
  pageSize?: number
  query?: string
}

export function useReceipts(params: UseReceiptsParams) {
  const key = ['receipts', params]
  return useQuery<PaginatedReceipts>({
    queryKey: key,
    queryFn: () => getReceipts({
      page: params.page,
      pageSize: params.pageSize,
      query: params.query,
    }),
    keepPreviousData: true,
    staleTime: 1000 * 60, // 1 minute
  })
}
