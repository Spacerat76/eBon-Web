import { useQuery } from '@tanstack/react-query'
import { getReports } from '../api/endpoints'
import type { ReportsResponse } from '../types/api'

type UseReportsParams = {
  dateFrom?: string
  dateTo?: string
  categoryIds?: number[]
}

export function useReports(params: UseReportsParams) {
  const key = ['reports', params]
  return useQuery<ReportsResponse>({
    queryKey: key,
    queryFn: () => getReports(params),
    staleTime: 1000 * 60 * 5,
  })
}
