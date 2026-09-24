/** Shared API response types. Phase 2+ will expand these as endpoints land. */

export interface HealthResponse {
  status: string
  service: string
  timestamp: string
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}
