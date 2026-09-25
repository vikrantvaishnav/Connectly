import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import { useAuthStore } from '../store/authStore'

export interface ConnectionSummary {
  incoming: number
  outgoing: number
  matches: number
}

/** Badge counts for the Requests/Matches inbox. Shared cache key across the app. */
export function useConnectionSummary() {
  const authUser = useAuthStore((s) => s.user)
  return useQuery({
    queryKey: ['connections-summary'],
    queryFn: async () => (await api.get<ConnectionSummary>('/connections/summary')).data,
    enabled: !!authUser,
    staleTime: 20_000,
    refetchInterval: 60_000,
  })
}
