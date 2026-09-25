import { create } from 'zustand'
import { api, setAccessToken, storeRefreshToken, getRefreshToken, clearTokens, refreshAccessToken } from '../lib/api'

export interface AuthUser {
  id: number
  username: string
  email: string
  role: string
  emailVerified: boolean
  totpEnabled: boolean
}

interface AuthState {
  user: AuthUser | null
  loading: boolean
  googleEnabled: boolean
  checked: boolean
  /** Present while a login is paused waiting for the 2FA code. */
  mfaToken: string | null

  bootstrap: () => Promise<void>
  login: (identifier: string, password: string) => Promise<'ok' | 'mfa'>
  verifyMfa: (code: string) => Promise<void>
  register: (input: {
    firstName: string
    lastName: string
    username: string
    email: string
    password: string
  }) => Promise<void>
  setUser: (user: AuthUser | null) => void
  logout: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  loading: false,
  googleEnabled: false,
  checked: false,
  mfaToken: null,

  bootstrap: async () => {
    // google status
    api.get('/auth/oauth/status').then((r) => set({ googleEnabled: r.data.googleEnabled })).catch(() => {})
    // session restore: rotate the stored refresh token into a fresh access token first
    if (!getRefreshToken()) {
      set({ checked: true })
      return
    }
    try {
      const access = await refreshAccessToken()
      if (!access) throw new Error('refresh failed')
      const me = await api.get<AuthUser>('/auth/me')
      set({ user: me.data, checked: true })
    } catch {
      clearTokens()
      set({ user: null, checked: true })
    }
  },

  login: async (identifier, password) => {
    const res = await api.post('/auth/login', { identifier, password })
    if ('mfaToken' in res.data) {
      set({ mfaToken: res.data.mfaToken })
      return 'mfa'
    }
    applyTokens(res.data)
    set({ user: res.data.user, mfaToken: null })
    return 'ok'
  },

  verifyMfa: async (code) => {
    const mfaToken = get().mfaToken
    if (!mfaToken) throw new Error('No pending two-factor challenge')
    const res = await api.post('/auth/mfa/verify', { mfaToken, code })
    applyTokens(res.data)
    set({ user: res.data.user, mfaToken: null })
  },

  register: async (input) => {
    // Registration now returns a full session (auto-login), so a lost or
    // undelivered verification email can never lock someone out.
    const res = await api.post<{ accessToken: string; refreshToken: string; user: AuthUser }>(
      '/auth/register',
      input,
    )
    applyTokens(res.data)
    set({ user: res.data.user })
  },

  setUser: (user) => set({ user }),

  logout: async () => {
    const refresh = getRefreshToken()
    if (refresh) {
      try {
        await api.post('/auth/logout', { refreshToken: refresh })
      } catch {
        // ignore — clearing locally regardless
      }
    }
    clearTokens()
    set({ user: null })
  },
}))

function applyTokens(data: { accessToken: string; refreshToken: string }) {
  setAccessToken(data.accessToken)
  storeRefreshToken(data.refreshToken)
}
