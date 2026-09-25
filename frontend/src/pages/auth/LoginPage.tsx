import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiErrorMessage } from '../../lib/api'
import { useAuthStore } from '../../store/authStore'
import { useAppStore } from '../../store/appStore'

export function LoginPage() {
  const login = useAuthStore((s) => s.login)
  const verifyMfa = useAuthStore((s) => s.verifyMfa)
  const mfaToken = useAuthStore((s) => s.mfaToken)
  const googleEnabled = useAuthStore((s) => s.googleEnabled)
  const pushToast = useAppStore((s) => s.pushToast)
  const navigate = useNavigate()

  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [code, setCode] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (useAuthStore.getState().mfaToken) {
        await verifyMfa(code)
        pushToast('Welcome back!', '👋')
        navigate('/home')
      } else {
        const result = await login(identifier, password)
        if (result === 'mfa') return // store now holds mfaToken; UI flips to code step
        pushToast('Welcome back!', '👋')
        navigate('/home')
      }
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--bg)] p-4">
      <div className="animate-fade-up w-full max-w-sm rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-8">
        <div className="mb-6 text-center">
          <p className="text-3xl font-bold text-indigo-400">Connectly</p>
          <p className="mt-1 text-sm text-[var(--muted)]">
            {mfaToken ? 'Two-factor verification' : 'Log in to your account'}
          </p>
        </div>

        <form className="space-y-4" onSubmit={submit}>
          {mfaToken ? (
            <>
              <p className="text-sm text-[var(--muted)]">
                Enter the 6-digit code from your authenticator app, or one of your recovery codes.
              </p>
              <input
                autoFocus
                value={code}
                onChange={(e) => { setCode(e.target.value); setError('') }}
                placeholder="123456 or XXXX-XXXX"
                className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2.5 text-center text-lg tracking-widest outline-none focus:border-[var(--accent)]"
              />
            </>
          ) : (
            <>
              <div>
                <label htmlFor="login-identifier" className="mb-1 block text-sm font-medium">Email or username</label>
                <input
                  id="login-identifier"
                  value={identifier}
                  onChange={(e) => { setIdentifier(e.target.value); setError('') }}
                  autoComplete="username"
                  className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2.5 text-sm outline-none focus:border-[var(--accent)]"
                />
              </div>
              <div>
                <label htmlFor="login-password" className="mb-1 block text-sm font-medium">Password</label>
                <input
                  id="login-password"
                  type="password"
                  value={password}
                  onChange={(e) => { setPassword(e.target.value); setError('') }}
                  autoComplete="current-password"
                  className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2.5 text-sm outline-none focus:border-[var(--accent)]"
                />
              </div>
            </>
          )}

          {error && <p className="rounded-lg bg-rose-500/10 px-3 py-2 text-xs text-rose-500">{error}</p>}

          <button
            type="submit"
            disabled={busy || (mfaToken ? code.trim().length < 6 : !identifier || !password)}
            className="w-full rounded-xl bg-[var(--accent)] py-2.5 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] active:scale-[0.98] disabled:opacity-40"
          >
            {busy ? 'Checking…' : mfaToken ? 'Verify' : 'Log in'}
          </button>
        </form>

        {!mfaToken && (
          <>
            {googleEnabled && (
              <>
                <div className="my-5 flex items-center gap-3 text-xs text-[var(--muted)]">
                  <span className="h-px flex-1 bg-[var(--border)]" /> <span className="h-px flex-1 bg-[var(--border)]" />
                </div>
                <a
                  href="/api/v1/auth/oauth/google"
                  className="flex w-full items-center justify-center gap-2 rounded-xl border border-[var(--border)] bg-[var(--surface-2)] py-2.5 text-sm font-medium transition-colors hover:bg-[var(--accent-soft)]"
                >
                  <span aria-hidden="true">G</span> Continue with Google
                </a>
              </>
            )}

            <div className="mt-4 flex justify-between text-xs">
              <Link to="/forgot-password" className="text-[var(--muted)] hover:text-[var(--text)]">Forgot password?</Link>
              <Link to="/register" className="font-medium text-indigo-400 hover:text-indigo-300">Create account</Link>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
