import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
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
            <Toggle on={false} onChange={() => pushToast('Private accounts arrive soon 🔒')} label="Private account" hint="Approve followers manually" />
          </>
        ) : (
          <p className="py-3 text-sm text-[var(--muted)]">Sign in to manage who can discover you.</p>
        )}
      </section>
    </div>
  )
}
