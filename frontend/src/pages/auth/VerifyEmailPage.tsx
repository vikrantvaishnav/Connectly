import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, apiErrorMessage } from '../../lib/api'
import { useAppStore } from '../../store/appStore'
import { useAuthStore } from '../../store/authStore'

/**
 * Email verification. Two modes:
 *  - /verify-email/pending   → waiting for the user to click the emailed link
 *  - /verify-email/:token    → verify immediately on mount
 *
 * Verification is optional for using Connectly (registration logs you in), so
 * this page also says so plainly — no more dead-end "check your email" wall.
 */
export function VerifyEmailPage() {
  const { token } = useParams()
  const pushToast = useAppStore((s) => s.pushToast)
  const user = useAuthStore((s) => s.user)
  const [status, setStatus] = useState<'pending' | 'verifying' | 'ok' | 'error'>(token ? 'verifying' : 'pending')
  const [message, setMessage] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [resent, setResent] = useState(false)
  const [resending, setResending] = useState(false)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    const controller = new AbortController()
    fetch('/api/v1/auth/verify-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
      signal: controller.signal,
    })
      .then(async (res) => {
        const body = (await res.json().catch(() => ({}))) as { message?: string }
        if (cancelled) return
        if (res.ok) {
          setStatus('ok')
          setMessage('Email verified!')
          pushToast('Email verified 🎉', '✅')
        } else {
          setStatus('error')
          setMessage(body.message ?? 'This verification link is invalid or has expired.')
        }
      })
      .catch(() => {
        if (!cancelled && !controller.signal.aborted) {
          setStatus('error')
          setMessage('Could not reach the server. Try again.')
        }
      })
    return () => {
      cancelled = true
      controller.abort()
    }
  }, [token, attempt, pushToast])

  const resend = async () => {
    setResending(true)
    try {
      await api.post('/auth/resend-verification')
      setResent(true)
      pushToast('Verification email sent', '📬')
    } catch (e) {
      pushToast(apiErrorMessage(e), '⚠️')
    } finally {
      setResending(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--bg)] p-4">
      <div className="animate-fade-up w-full max-w-sm rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-8 text-center">
        {status === 'verifying' && (
          <>
            <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-[var(--border)] border-t-indigo-400" />
            <h1 className="mt-4 text-lg font-semibold">Verifying your email…</h1>
          </>
        )}
        {status === 'pending' && (
          <>
            <p className="text-4xl">📬</p>
            <h1 className="mt-4 text-lg font-semibold">Verify your email</h1>
            <p className="mt-2 text-sm text-[var(--muted)]">
              {user
                ? <>We're sending a verification link to <strong>{user.email}</strong>. Click it to confirm your address.</>
                : 'Sign in and open this page to resend a verification link.'}
            </p>
            {user && (
              <button
                onClick={() => void resend()}
                disabled={resending || resent}
                className="mt-3 text-sm font-medium text-indigo-400 hover:text-indigo-300 disabled:opacity-50"
              >
                {resent ? 'Sent ✓ (check spam too)' : resending ? 'Sending…' : 'Resend email'}
              </button>
            )}
          </>
        )}
        {status === 'ok' && (
          <>
            <p className="text-4xl">✅</p>
            <h1 className="mt-4 text-lg font-semibold">Email verified</h1>
            <p className="mt-2 text-sm text-[var(--muted)]">{message}</p>
          </>
        )}
        {status === 'error' && (
          <>
            <p className="text-4xl">⚠️</p>
            <h1 className="mt-4 text-lg font-semibold">Verification failed</h1>
            <p className="mt-2 text-sm text-[var(--muted)]">{message}</p>
            <button
              onClick={() => { setStatus('verifying'); setAttempt((n) => n + 1) }}
              className="mt-3 text-sm font-medium text-indigo-400 hover:text-indigo-300"
            >
              Try again
            </button>
          </>
        )}

        <Link
          to="/home"
          className="mt-6 inline-block rounded-xl bg-[var(--accent)] px-5 py-2.5 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)]"
        >
          Continue to Connectly
        </Link>
        <p className="mt-3 text-xs text-[var(--muted)]">
          Verifying is optional — you can already use everything. It only matters for
          password recovery.
        </p>
      </div>
    </div>
  )
}
