import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiErrorMessage } from '../lib/api'
import { useAppStore } from '../store/appStore'
import { Avatar } from '../components/Avatar'
import { useAuthStore } from '../store/authStore'

function Toggle({ on, onChange, label, hint }: { on: boolean; onChange: () => void; label: string; hint?: string }) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-4 py-3">
      <span>
        <span className="block text-sm font-medium">{label}</span>
        {hint && <span className="block text-xs text-[var(--muted)]">{hint}</span>}
      </span>
      <button
        type="button"
        role="switch"
        aria-checked={on}
        onClick={onChange}
        className={`relative h-6 w-11 shrink-0 rounded-full transition-colors ${on ? 'bg-[var(--accent)]' : 'bg-[var(--border)]'}`}
      >
        <span
          className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-all ${on ? 'left-[1.375rem]' : 'left-0.5'}`}
        />
      </button>
    </label>
  )
}

export function SettingsPage() {
  const theme = useAppStore((s) => s.theme)
  const toggleTheme = useAppStore((s) => s.toggleTheme)
  const pushToast = useAppStore((s) => s.pushToast)
  const authUser = useAuthStore((s) => s.user)
  const [discoverable, setDiscoverable] = useState(false)

  // Live privacy state for signed-in users.
  useEffect(() => {
    if (!authUser) return
    api.get<{ discoverable: boolean }>('/users/me/location-status')
      .then((res) => setDiscoverable(res.data.discoverable))
      .catch(() => {})
  }, [authUser])

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-4 sm:p-6">
      <h1 className="text-2xl font-bold">Settings</h1>

      {/* Account */}
      <section className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-[var(--muted)]">Account</h2>
        {authUser ? (
          <div className="flex items-center gap-3">
            <Avatar name={authUser.username} id={String(authUser.id)} size="lg" />
            <div>
              <p className="font-semibold">@{authUser.username}</p>
              <p className="text-sm text-[var(--muted)]">
                {authUser.emailVerified ? 'Email verified ✓' : 'Email not verified'}
              </p>
            </div>
          </div>
        ) : (
          <p className="text-sm text-[var(--muted)]">
            <Link to="/login" className="text-indigo-400 hover:underline">Sign in</Link> to manage your
            account, privacy and security.
          </p>
        )}
        <p className="mt-3 text-xs text-[var(--muted)]">
          Manage your email, password and account security from the{' '}
          <Link to="/settings/security" className="text-indigo-400 hover:underline">Security settings</Link>.
        </p>
      </section>

      {/* Security */}
      <section className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5">
        <h2 className="mb-1 text-sm font-semibold uppercase tracking-wide text-[var(--muted)]">Security</h2>
        <div className="flex items-center justify-between gap-4 py-3">
          <div>
            <p className="text-sm font-medium">Two-factor authentication & sessions</p>
            <p className="text-xs text-[var(--muted)]">
              {authUser
                ? authUser.totpEnabled
                  ? '2FA is ON · manage devices and recovery codes'
                  : '2FA is OFF · add an authenticator app'
                : 'Sign in to manage 2FA and active devices'}
            </p>
          </div>
          <Link
            to="/settings/security"
            className="shrink-0 rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)]"
          >
            Manage
          </Link>
        </div>
      </section>

      {/* Appearance */}
      <section className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5">
        <h2 className="mb-1 text-sm font-semibold uppercase tracking-wide text-[var(--muted)]">Appearance</h2>
        <Toggle
          on={theme === 'dark'}
          onChange={toggleTheme}
          label="Dark mode"
          hint="Applies instantly across the app"
        />
      </section>

      {/* Privacy */}
      <section className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5">
        <h2 className="mb-1 text-sm font-semibold uppercase tracking-wide text-[var(--muted)]">Privacy</h2>
        {authUser ? (
          <>
            <Toggle
              on={discoverable}
              onChange={() => {
                api.put('/users/me/discoverability', { discoverable: !discoverable })
                  .then((res: { data: { discoverable: boolean } }) => {
                    setDiscoverable(res.data.discoverable)
                    pushToast(res.data.discoverable ? 'You are visible on Nearby' : 'You are hidden from Nearby', '🛡️')
                  })
                  .catch((e: unknown) => pushToast(apiErrorMessage(e), '⚠️'))
              }}
              label="Discoverable nearby"
              hint="Let people near you see you in Nearby — backed by your live account"
            />
            <Toggle on label="Approximate distance only" onChange={() => {}} hint="Nearby never shows exact coordinates — enforced server-side" />
            <PrivateAccountToggle />
          </>
        ) : (
          <p className="py-3 text-sm text-[var(--muted)]">Sign in to manage who can discover you.</p>
        )}
      </section>

      {authUser && <SafetySection />}

      {authUser && <DangerZone />}
    </div>
  )
}

/** Private-account mode: follows become requests you approve. */
function PrivateAccountToggle() {
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()
  const state = useQuery({
    queryKey: ['me-profile'],
    queryFn: async () => (await api.get<{ accountPrivate: boolean }>('/users/me')).data,
  })
  const setPrivate = useMutation({
    mutationFn: (on: boolean) => api.put<{ accountPrivate: boolean }>('/users/me/privacy', { accountPrivate: on }),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: ['me-profile'] })
      pushToast(res.data.accountPrivate
        ? 'Your account is private — new followers need your approval'
        : 'Your account is public', '🛡️')
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })
  return (
    <Toggle
      on={state.data?.accountPrivate ?? false}
      onChange={() => setPrivate.mutate(!(state.data?.accountPrivate ?? false))}
      label="Private account"
      hint="Approve followers manually; strangers see only your username and follower count"
    />
  )
}

/** Blocked & muted users — live from the safety API, with instant unblock/unmute. */
function SafetySection() {
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()
  const lists = useQuery({
    queryKey: ['safety-lists'],
    queryFn: async () =>
      (await api.get<{ blocked: SafetyEntry[]; muted: SafetyEntry[] }>('/safety/lists')).data,
  })

  const lift = useMutation({
    mutationFn: ({ id, kind }: { id: number; kind: 'block' | 'mute' }) =>
      kind === 'block' ? api.delete(`/users/${id}/block`) : api.delete(`/users/${id}/mute`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['safety-lists'] })
      pushToast('Done ✓', '✅')
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const blocked = lists.data?.blocked ?? []
  const muted = lists.data?.muted ?? []

  return (
    <section className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5">
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-[var(--muted)]">Blocked & muted</h2>
      {lists.isPending && <div className="h-16 animate-pulse rounded-xl bg-[var(--surface-2)]" />}
      {!lists.isPending && blocked.length === 0 && muted.length === 0 && (
        <p className="text-sm text-[var(--muted)]">
          Nobody blocked or muted. Use the Block button on someone's profile if you ever need it.
        </p>
      )}
      {blocked.length > 0 && (
        <>
          <p className="mb-2 text-xs font-semibold text-[var(--muted)]">Blocked ({blocked.length})</p>
          <div className="mb-4 space-y-2">
            {blocked.map((u) => (
              <SafetyRow key={`b-${u.id}`} entry={u} kind="block" onLift={lift.mutate} />
            ))}
          </div>
        </>
      )}
      {muted.length > 0 && (
        <>
          <p className="mb-2 text-xs font-semibold text-[var(--muted)]">Muted ({muted.length})</p>
          <div className="space-y-2">
            {muted.map((u) => (
              <SafetyRow key={`m-${u.id}`} entry={u} kind="mute" onLift={lift.mutate} />
            ))}
          </div>
        </>
      )}
    </section>
  )
}

interface SafetyEntry {
  id: number
  username: string
  createdAt: string
  kind: string
}

function SafetyRow({ entry, kind, onLift }: { entry: SafetyEntry; kind: 'block' | 'mute'; onLift: (v: { id: number; kind: 'block' | 'mute' }) => void }) {
  return (
    <div className="flex items-center gap-3 rounded-xl bg-[var(--surface-2)] p-3">
      <div className="flex-1">
        <p className="text-sm font-medium">@{entry.username}</p>
        <p className="text-xs text-[var(--muted)]">{kind === 'block' ? 'Blocked' : 'Muted'}</p>
      </div>
      <button
        onClick={() => onLift({ id: entry.id, kind })}
        className="rounded-lg bg-[var(--accent)] px-3 py-1.5 text-xs font-medium text-white hover:bg-[var(--accent-hover)]"
      >
        {kind === 'block' ? 'Unblock' : 'Unmute'}
      </button>
    </div>
  )
}

/** Data & Compliance: self-service account deletion, double-confirmed. */
function DangerZone() {
  const pushToast = useAppStore((s) => s.pushToast)
  const logout = useAuthStore((s) => s.logout)
  const [confirming, setConfirming] = useState(false)
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)

  const remove = async () => {
    setBusy(true)
    try {
      await api.delete('/auth/me')
      await logout()
      window.location.href = '/register'
    } catch (e) {
      pushToast(apiErrorMessage(e), '⚠️')
      setBusy(false)
    }
  }

  return (
    <section className="rounded-2xl border border-rose-500/30 bg-rose-500/5 p-5">
      <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-rose-400">Delete account</h2>
      {!confirming ? (
        <>
          <p className="text-sm text-[var(--muted)]">
            Permanently removes your account, posts, messages, matches and everything else.
            This cannot be undone.
          </p>
          <button
            onClick={() => setConfirming(true)}
            className="mt-3 rounded-xl border border-rose-500/50 px-4 py-2 text-sm font-medium text-rose-400 hover:bg-rose-500/10"
          >
            Delete my account…
          </button>
        </>
      ) : (
        <>
          <p className="text-sm">
            Type <strong>DELETE</strong> to confirm. Everything goes — there is no undo.
          </p>
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="DELETE"
            className="mt-2 w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm outline-none focus:border-rose-500"
          />
          <div className="mt-3 flex gap-2">
            <button
              onClick={() => { setConfirming(false); setText('') }}
              className="rounded-xl border border-[var(--border)] px-4 py-2 text-sm font-medium hover:bg-[var(--surface-2)]"
            >
              Keep my account
            </button>
            <button
              onClick={() => void remove()}
              disabled={text !== 'DELETE' || busy}
              className="rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-500 disabled:opacity-40"
            >
              {busy ? 'Deleting…' : 'Permanently delete'}
            </button>
          </div>
        </>
      )}
    </section>
  )
}
