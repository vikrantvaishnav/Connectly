import { useCallback, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { QRCodeSVG } from 'qrcode.react'
import { api, apiErrorMessage, clearTokens } from '../lib/api'
import { useAuthStore } from '../store/authStore'
import { useAppStore } from '../store/appStore'

interface SessionDto {
  id: number
  device: string
  ip: string
  createdAt: string
  lastUsedAt: string
  current: boolean
}

function fmt(iso: string): string {
  try {
    return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
  } catch {
    return iso
  }
}

function Card({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-[var(--muted)]">{title}</h2>
      {subtitle && <p className="mt-1 text-xs text-[var(--muted)]">{subtitle}</p>}
      <div className="mt-3">{children}</div>
    </section>
  )
}

export function SecuritySettingsPage() {
  const user = useAuthStore((s) => s.user)
  const checked = useAuthStore((s) => s.checked)
  const setUser = useAuthStore((s) => s.setUser)
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()

  // Sessions via TanStack Query
  const sessionsQuery = useQuery({
    queryKey: ['sessions'],
    queryFn: async () => {
      const res = await api.get<SessionDto[]>('/auth/sessions')
      return res.data
    },
    enabled: !!user,
    retry: false,
  })
  const sessions = sessionsQuery.data ?? null
  const offline = sessionsQuery.isError

  // 2FA state
  const [setup, setSetup] = useState<{ secret: string; otpauthUrl: string } | null>(null)
  const [totpCode, setTotpCode] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null)
  const [disablePassword, setDisablePassword] = useState('')
  const [showDisable, setShowDisable] = useState(false)
  const [busy2fa, setBusy2fa] = useState(false)
  const [err2fa, setErr2fa] = useState('')
  const [revokingId, setRevokingId] = useState<number | null>(null)

  const begin2fa = useCallback(async () => {
    setBusy2fa(true)
    setErr2fa('')
    try {
      const res = await api.post<{ secret: string; otpauthUrl: string }>('/auth/2fa/setup')
      setSetup(res.data)
    } catch (err) {
      setErr2fa(apiErrorMessage(err))
    } finally {
      setBusy2fa(false)
    }
  }, [])

  const confirm2fa = useCallback(async () => {
    setBusy2fa(true)
    setErr2fa('')
    try {
      const res = await api.post<{ codes: string[] }>('/auth/2fa/enable', { code: totpCode.trim() })
      setRecoveryCodes(res.data.codes)
      setSetup(null)
      setTotpCode('')
      if (user) setUser({ ...user, totpEnabled: true })
      pushToast('Two-factor authentication enabled 🔐', '🛡️')
      queryClient.invalidateQueries({ queryKey: ['sessions'] })
    } catch (err) {
      setErr2fa(apiErrorMessage(err))
    } finally {
      setBusy2fa(false)
    }
  }, [totpCode, user, setUser, pushToast, queryClient])

  const disable2fa = useCallback(async () => {
    setBusy2fa(true)
    setErr2fa('')
    try {
      await api.post('/auth/2fa/disable', { password: disablePassword })
      setShowDisable(false)
      setDisablePassword('')
      if (user) setUser({ ...user, totpEnabled: false })
      pushToast('Two-factor authentication disabled', '🔓')
    } catch (err) {
      setErr2fa(apiErrorMessage(err))
    } finally {
      setBusy2fa(false)
    }
  }, [disablePassword, user, setUser, pushToast])

  const revoke = useCallback(async (id: number) => {
    setRevokingId(id)
    try {
      await api.delete(`/auth/sessions/${id}`)
      queryClient.invalidateQueries({ queryKey: ['sessions'] })
      pushToast('Session revoked', '🚪')
    } catch (err) {
      pushToast(apiErrorMessage(err), '⚠️')
    } finally {
      setRevokingId(null)
    }
  }, [queryClient, pushToast])

  const logoutAll = useCallback(async () => {
    try {
      await api.post('/auth/logout-all')
      clearTokens()
      useAuthStore.setState({ user: null })
      window.location.href = '/login'
    } catch (err) {
      pushToast(apiErrorMessage(err), '⚠️')
    }
  }, [pushToast])

  if (!user) {
    return (
      <div className="mx-auto max-w-2xl space-y-5 p-4 sm:p-6">
        <h1 className="text-2xl font-bold">Security</h1>
        {!checked && <p className="text-sm text-[var(--muted)]">Checking your session…</p>}
        <Card title="Not signed in">
          <p className="text-sm text-[var(--muted)]">
            Sign in to manage two-factor authentication and review where you're logged in.
          </p>
          <a href="/login" className="mt-3 inline-block rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)]">
            Go to login
          </a>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-4 sm:p-6">
      <h1 className="text-2xl font-bold">Security</h1>

      {offline && (
        <Card title="Backend offline">
          <p className="text-sm text-[var(--muted)]">
            These settings are live API features. Start the backend (<code className="rounded bg-[var(--surface-2)] px-1">cd backend &amp;&amp; ./mvnw spring-boot:run</code>)
            to manage real sessions and 2FA.
          </p>
        </Card>
      )}

      {/* Two-factor authentication */}
      <Card title="Two-factor authentication" subtitle="Protect your account with an authenticator app (TOTP) plus recovery codes.">
        {recoveryCodes && (
          <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4">
            <p className="text-sm font-semibold text-emerald-600 dark:text-emerald-400">Save your recovery codes now</p>
            <p className="mt-1 text-xs text-[var(--muted)]">
              Each code works once if you lose your authenticator. They are shown only this time.
            </p>
            <div className="mt-3 grid grid-cols-2 gap-2 font-mono text-sm">
              {recoveryCodes.map((c) => (
                <span key={c} className="rounded-lg bg-[var(--surface-2)] px-2 py-1">{c}</span>
              ))}
            </div>
            <div className="mt-3 flex gap-2">
              <button
                onClick={() => { navigator.clipboard.writeText(recoveryCodes.join('\n')); pushToast('Recovery codes copied', '📋') }}
                className="rounded-lg bg-[var(--accent)] px-3 py-1.5 text-xs font-medium text-white hover:bg-[var(--accent-hover)]"
              >
                Copy codes
              </button>
              <button onClick={() => setRecoveryCodes(null)} className="rounded-lg border border-[var(--border)] px-3 py-1.5 text-xs font-medium hover:bg-[var(--surface-2)]">
                Done
              </button>
            </div>
          </div>
        )}

        {!recoveryCodes && !user.totpEnabled && !setup && (
          <div>
            <p className="text-sm text-[var(--muted)]">Status: <span className="font-medium text-amber-500">Off</span></p>
            <button
              onClick={begin2fa}
              disabled={busy2fa}
              className="mt-3 rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)] disabled:opacity-40"
            >
              {busy2fa ? 'Preparing…' : 'Enable 2FA'}
            </button>
          </div>
        )}

        {setup && (
          <div className="space-y-4">
            <ol className="list-inside list-decimal space-y-1 text-sm text-[var(--muted)]">
              <li>Add the key below in Google Authenticator / Authy / 1Password.</li>
              <li>Enter the current 6-digit code to confirm.</li>
            </ol>
            <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-start">
              <div className="rounded-xl bg-white p-3 shadow-sm">
                <QRCodeSVG value={setup.otpauthUrl} size={148} level="M" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-xs text-[var(--muted)]">Manual key</p>
                <code className="mt-1 block break-all rounded-lg bg-[var(--surface-2)] px-2 py-1.5 font-mono text-xs">{setup.secret}</code>
                <div className="mt-3 flex gap-2">
                  <input
                    inputMode="numeric"
                    maxLength={6}
                    value={totpCode}
                    onChange={(e) => { setTotpCode(e.target.value.replace(/\D/g, '')); setErr2fa('') }}
                    placeholder="123456"
                    className="w-32 rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-center font-mono text-lg tracking-widest outline-none focus:border-[var(--accent)]"
                  />
                  <button
                    onClick={confirm2fa}
                    disabled={busy2fa || totpCode.length !== 6}
                    className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)] disabled:opacity-40"
                  >
                    {busy2fa ? 'Verifying…' : 'Confirm'}
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {user.totpEnabled && !recoveryCodes && (
          <div>
            <p className="text-sm text-[var(--muted)]">
              Status: <span className="font-medium text-emerald-500">On</span> — your account requires a 6-digit code at login.
            </p>
            {!showDisable ? (
              <button onClick={() => setShowDisable(true)} className="mt-3 rounded-xl border border-rose-500/40 px-4 py-2 text-sm font-medium text-rose-500 hover:bg-rose-500/10">
                Disable 2FA
              </button>
            ) : (
              <div className="mt-3 flex flex-wrap items-center gap-2">
                <input
                  type="password"
                  value={disablePassword}
                  onChange={(e) => { setDisablePassword(e.target.value); setErr2fa('') }}
                  placeholder="Confirm your password"
                  className="w-56 rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
                />
                <button onClick={disable2fa} disabled={busy2fa || !disablePassword} className="rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-500 disabled:opacity-40">
                  {busy2fa ? 'Disabling…' : 'Confirm disable'}
                </button>
                <button onClick={() => setShowDisable(false)} className="rounded-xl border border-[var(--border)] px-3 py-2 text-sm hover:bg-[var(--surface-2)]">
                  Cancel
                </button>
              </div>
            )}
          </div>
        )}

        {err2fa && <p className="mt-3 rounded-lg bg-rose-500/10 px-3 py-2 text-xs text-rose-500">{err2fa}</p>}
      </Card>

      {/* Sessions */}
      <Card title="Where you're logged in" subtitle="Every active session with its device and IP. Revoke anything you don't recognise.">
        {sessionsQuery.isPending && !offline && <p className="text-sm text-[var(--muted)]">Loading sessions…</p>}
        {sessions !== null && sessions.length === 0 && (
          <p className="text-sm text-[var(--muted)]">No active sessions.</p>
        )}
        {sessions !== null && sessions.length > 0 && (
          <div className="space-y-2">
            {sessions.map((s) => (
              <div key={s.id} className={`flex items-center gap-3 rounded-xl p-3 ${s.current ? 'border border-emerald-500/30 bg-emerald-500/5' : 'bg-[var(--surface-2)]'}`}>
                <span className="text-xl">{s.current ? '📍' : '💻'}</span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">
                    {s.device}
                    {s.current && <span className="ml-2 rounded-full bg-emerald-500/15 px-2 py-0.5 text-[10px] font-semibold text-emerald-600 dark:text-emerald-400">THIS DEVICE</span>}
                  </p>
                  <p className="text-xs text-[var(--muted)]">{s.ip} · started {fmt(s.createdAt)} · active {fmt(s.lastUsedAt)}</p>
                </div>
                {!s.current && (
                  <button
                    onClick={() => revoke(s.id)}
                    disabled={revokingId === s.id}
                    className="rounded-lg border border-[var(--border)] px-3 py-1.5 text-xs font-medium hover:bg-rose-500/10 hover:text-rose-500 disabled:opacity-40"
                  >
                    {revokingId === s.id ? '…' : 'Revoke'}
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
        {sessions !== null && sessions.length > 1 && (
          <button onClick={logoutAll} className="mt-3 rounded-xl border border-rose-500/40 px-4 py-2 text-sm font-medium text-rose-500 hover:bg-rose-500/10">
            Log out all devices
          </button>
        )}
      </Card>

      <p className="pb-6 text-center text-xs text-[var(--muted)]">
        Security events (logins, 2FA changes, revocations) are recorded in the backend audit log.
      </p>
    </div>
  )
}
