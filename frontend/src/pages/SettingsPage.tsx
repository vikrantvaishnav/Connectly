import { Link } from 'react-router-dom'
import { useAppStore } from '../store/appStore'
import { Avatar } from '../components/Avatar'
import { useAuthStore } from '../store/authStore'
import { CURRENT_USER_ID } from '../data/demo'

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
  const users = useAppStore((s) => s.users)
  const blockedUserIds = useAppStore((s) => s.blockedUserIds)
  const toggleBlock = useAppStore((s) => s.toggleBlock)
  const pushToast = useAppStore((s) => s.pushToast)
  const me = users.find((u) => u.id === CURRENT_USER_ID)
  const blockedUsers = users.filter((u) => blockedUserIds.includes(u.id))
  const authUser = useAuthStore((s) => s.user)

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-4 sm:p-6">
      <h1 className="text-2xl font-bold">Settings</h1>

      {/* Account */}
      <section className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-[var(--muted)]">Account</h2>
        {me && (
          <div className="flex items-center gap-3">
            <Avatar name={me.name} id={me.id} size="lg" online />
            <div>
              <p className="font-semibold">{me.name}</p>
              <p className="text-sm text-[var(--muted)]">@{me.username} · demo account</p>
            </div>
          </div>
        )}
        <p className="mt-3 text-xs text-[var(--muted)]">
          Real account management (email, password change, deletion) arrives with Phase 2 authentication.
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
          hint="Currently wired to the live theme system"
        />
      </section>

      {/* Privacy */}
      <section className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5">
        <h2 className="mb-1 text-sm font-semibold uppercase tracking-wide text-[var(--muted)]">Privacy</h2>
        <Toggle on onChange={() => pushToast('Persisted privacy settings arrive in Phase 4 🔒')} label="Discoverable nearby" hint="Let people near you see you in Nearby" />
        <Toggle on onChange={() => pushToast('Persisted privacy settings arrive in Phase 4 🔒')} label="Show approximate distance" hint="Never your exact coordinates" />
        <Toggle on={false} onChange={() => pushToast('Persisted privacy settings arrive in Phase 4 🔒')} label="Private account" hint="Approve followers manually" />
      </section>

      {/* Blocked users */}
      <section className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-[var(--muted)]">Blocked users</h2>
        {blockedUsers.length === 0 ? (
          <p className="text-sm text-[var(--muted)]">
            No blocked users. Block someone from their profile to test this.
          </p>
        ) : (
          <div className="space-y-2">
            {blockedUsers.map((u) => (
              <div key={u.id} className="flex items-center gap-3 rounded-xl bg-[var(--surface-2)] p-3">
                <Avatar name={u.name} id={u.id} size="sm" />
                <div className="flex-1">
                  <p className="text-sm font-medium">{u.name}</p>
                  <p className="text-xs text-[var(--muted)]">@{u.username}</p>
                </div>
                <button
                  onClick={() => toggleBlock(u.id)}
                  className="rounded-lg bg-[var(--accent)] px-3 py-1.5 text-xs font-medium text-white hover:bg-[var(--accent-hover)]"
                >
                  Unblock
                </button>
              </div>
            ))}
          </div>
        )}
      </section>

      <p className="pb-6 text-center text-xs text-[var(--muted)]">
        Connectly demo · Security, notification and appearance settings become fully persistable in Phase 2+.
      </p>
    </div>
  )
}
