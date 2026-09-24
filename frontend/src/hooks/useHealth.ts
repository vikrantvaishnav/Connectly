import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: async () => {
      const res = await api.get<{ status: string; service: string; timestamp: string }>('/health')
      return res.data
    },
    refetchInterval: 15_000,
    retry: false,
  })
}
