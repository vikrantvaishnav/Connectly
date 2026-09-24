import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../lib/api'
import { useAppStore } from '../../store/appStore'

export function ForgotPasswordPage() {
  const pushToast = useAppStore((s) => s.pushToast)
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await api.post('/auth/forgot-password', { email })
    } catch {
      // deliberately ignore: never reveal whether an account exists
    } finally {
      setBusy(false)
      setSent(true)
      pushToast('If that account exists, a reset link was sent (check backend console in dev).', '🔐')
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--bg)] p-4">
      <div className="animate-fade-up w-full max-w-sm rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-8">
        {sent ? (
          <div className="text-center">
            <p className="text-4xl">📨</p>
            <h1 className="mt-4 text-lg font-semibold">Check your email</h1>
            <p className="mt-2 text-sm text-[var(--muted)]">
              If an account exists for <strong>{email}</strong>, a password reset link is on its way. The link
              expires in 30 minutes.
            </p>
            <Link to="/login" className="mt-6 inline-block text-sm font-medium text-indigo-400 hover:text-indigo-300">
              ← Back to login
            </Link>
          </div>
        ) : (
          <>
            <h1 className="text-lg font-semibold">Forgot password</h1>
            <p className="mt-1 text-sm text-[var(--muted)]">We'll email you a secure reset link.</p>
            <form className="mt-5 space-y-4" onSubmit={submit}>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2.5 text-sm outline-none focus:border-[var(--accent)]"
              />
              {error && <p className="text-xs text-rose-500">{error}</p>}
              <button
                type="submit"
                disabled={busy || !email}
                className="w-full rounded-xl bg-[var(--accent)] py-2.5 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] disabled:opacity-40"
              >
                {busy ? 'Sending…' : 'Send reset link'}
              </button>
            </form>
            <p className="mt-4 text-center text-xs text-[var(--muted)]">
              <Link to="/login" className="hover:text-[var(--text)]">← Back to login</Link>
            </p>
          </>
        )}
      </div>
    </div>
  )
}
