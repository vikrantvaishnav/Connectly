import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * UI-only client state: toasts, theme, geolocation.
 *
 * Everything that used to live here as canned demo data (users, posts,
 * conversations, notifications, fake auto-replies, fake "they accepted your
 * request") is gone. Signed-out visitors see sign-in prompts, and every
 * signed-in view reads the real API through TanStack Query. Nothing about
 * the product is simulated anymore.
 */

type GeoStatus = 'idle' | 'prompt' | 'granted' | 'denied' | 'unavailable'

export interface Toast {
  id: number
  text: string
  icon?: string
}

interface AppState {
  toasts: Toast[]
  theme: 'dark' | 'light'
  /** Coordinates are null until the user actually grants permission — no fake position. */
  geo: { status: GeoStatus; lat: number | null; lng: number | null; accuracyKm: number | null }

  pushToast: (text: string, icon?: string) => void
  dismissToast: (id: number) => void
  toggleTheme: () => void
  setGeo: (g: AppState['geo']) => void
}

let toastId = 0

export const useAppStore = create<AppState>()(
  persist(
    (set, get) => ({
      toasts: [],
      theme: 'dark',
      geo: { status: 'idle', lat: null, lng: null, accuracyKm: null },

      pushToast: (text, icon) => {
        const id = ++toastId
        set((s) => ({ toasts: [...s.toasts, { id, text, icon }] }))
        setTimeout(() => get().dismissToast(id), 2600)
      },

      dismissToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),

      toggleTheme: () =>
        set((s) => ({ theme: s.theme === 'dark' ? 'light' : 'dark' })),

      setGeo: (g) => set({ geo: g }),
    }),
    {
      name: 'connectly-ui',
      partialize: (s) => ({ theme: s.theme }),
    },
  ),
)

// Apply theme class whenever theme changes
if (typeof document !== 'undefined') {
  const apply = (t: 'dark' | 'light') => {
    document.documentElement.classList.toggle('dark', t === 'dark')
  }
  apply(useAppStore.getState().theme)
  useAppStore.subscribe((s, prev) => {
    if (s.theme !== prev.theme) apply(s.theme)
  })
}
