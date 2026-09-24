import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, apiErrorMessage } from '../../lib/api'
import { useAppStore } from '../../store/appStore'

export function ResetPasswordPage() {
  const { token } = useParams()
  const navigate = useNavigate()
  const pushToast = useAppStore((s) => s.pushToast)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const valid = password.length >= 10 && password === confirm

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!valid) {
      setError(password.length < 10 ? 'At least 10 characters' : 'Passwords do not match')
      return
    }
    setBusy(true)
    setError('')
    try {
      await api.post('/auth/reset-password', { token, password })
      pushToast('Password updated — log in with your new password.', '🔑')
      navigate('/login')
    } catch (err) {
      setError(apiErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (!token) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[var(--bg)] p-4">
        <div className="w-full max-w-sm rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-8 text-center">
          <p className="text-4xl">🔗</p>
          <h1 className="mt-4 text-lg font-semibold">Missing reset token</h1>
          <p className="mt-2 text-sm text-[var(--muted)]">Open the reset link from your email.</p>
          <Link to="/forgot-password" className="mt-6 inline-block text-sm font-medium text-indigo-400">Request a new link</Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--bg)] p-4">
      <div className="animate-fade-up w-full max-w-sm rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-8">
        <h1 className="text-lg font-semibold">Choose a new password</h1>
        <p className="mt-1 text-sm text-[var(--muted)]">It will log out every active session.</p>
        <form className="mt-5 space-y-4" onSubmit={submit}>
          <input
            type="password"
            value={password}
            onChange={(e) => { setPassword(e.target.value); setError('') }}
            placeholder="New password (10+ characters)"
            className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2.5 text-sm outline-none focus:border-[var(--accent)]"
          />
          <input
            type="password"
            value={confirm}
            onChange={(e) => { setConfirm(e.target.value); setError('') }}
            placeholder="Confirm new password"
            className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2.5 text-sm outline-none focus:border-[var(--accent)]"
          />
          {error && <p className="rounded-lg bg-rose-500/10 px-3 py-2 text-xs text-rose-500">{error}</p>}
          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-xl bg-[var(--accent)] py-2.5 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] disabled:opacity-40"
          >
            {busy ? 'Saving…' : 'Reset password'}
          </button>
        </form>
      </div>
    </div>
  )
}
