import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

// Same-origin in dev (vite proxies /api → :8080) and in prod (nginx does the same).
const API_BASE = '/api/v1'

export const api = axios.create({
  baseURL: API_BASE,
  // Generous timeout: on Render's free tier the backend sleeps after 15 min idle
  // (~50s wake) and cold queries against the cloud DB can take several seconds.
  timeout: 45_000,
})

/** Correct WebSocket URL for the current origin (https pages MUST use wss://). */
export function wsUrl(): string {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${location.host}/ws/chat`
}

// ---------- token plumbing ----------

let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

const REFRESH_KEY = 'connectly.refresh'

export function storeRefreshToken(token: string) {
  localStorage.setItem(REFRESH_KEY, token)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function clearTokens() {
  accessToken = null
  localStorage.removeItem(REFRESH_KEY)
  window.dispatchEvent(new Event('connectly:logout'))
}

// ---------- request interceptor ----------

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

// ---------- refresh queue (single-flight) ----------

let refreshing: Promise<string | null> | null = null

async function doRefresh(): Promise<string | null> {
  const refresh = getRefreshToken()
  if (!refresh) return null
  try {
    // plain axios: no interceptors here
    const res = await axios.post(`${API_BASE}/auth/refresh`, { refreshToken: refresh })
    const { accessToken: nextAccess, refreshToken: nextRefresh } = res.data
    accessToken = nextAccess
    storeRefreshToken(nextRefresh)
    return nextAccess
  } catch {
    clearTokens()
    return null
  }
}

/** Single-flight refresh; returns the new access token or null. */
export async function refreshAccessToken(): Promise<string | null> {
  refreshing = refreshing ?? doRefresh()
  const token = await refreshing
  refreshing = null
  return token
}

// URLs that must never trigger the refresh-retry loop (they are pre-auth by design).
const NO_REFRESH_URLS = ['/auth/refresh', '/auth/login', '/auth/mfa/verify']

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    const noRefresh = NO_REFRESH_URLS.some((u) => original?.url?.includes(u))
    if (error.response?.status === 401 && original && !original._retried && !noRefresh) {
      original._retried = true
      const token = await refreshAccessToken()
      if (token) {
        original.headers.Authorization = `Bearer ${token}`
        return api(original)
      }
    }
    return Promise.reject(error)
  },
)

/** Human-friendly message from an API error response. */
export function apiErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { message?: string } | undefined
    if (data?.message) return data.message
    if (err.response?.status === 429) return 'Too many requests — slow down and try again.'
  }
  return 'Something went wrong. Try again.'
}
