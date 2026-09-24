import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { setAccessToken, storeRefreshToken } from '../../lib/api'

/**
 * Landing page after Google redirects back to /oauth/callback#access=...&refresh=...
 * Tokens ride in the URL fragment (never sent to the server), base64url-encoded.
 */
function decodeBase64Url(v: string): string {
  const b64 = v.replace(/-/g, '+').replace(/_/g, '/')
  return new TextDecoder().decode(Uint8Array.from(atob(b64), (c) => c.charCodeAt(0)))
}

type Outcome =
  | { status: 'ok'; access: string; refresh: string }
  | { status: 'error'; message: string }

function readOutcome(): Outcome {
  const frag = new URLSearchParams(window.location.hash.replace(/^#/, ''))
  const access = frag.get('access')
  const refresh = frag.get('refresh')
  if (!access || !refresh) {
    return { status: 'error', message: 'Sign-in did not return tokens. Please try again.' }
  }
  try {
    return { status: 'ok', access: decodeBase64Url(access), refresh: decodeBase64Url(refresh) }
  } catch {
    return { status: 'error', message: 'Invalid token payload in callback.' }
  }
}

export function OAuthCallbackPage() {
  // Derived synchronously from the URL — the effect below only performs side effects.
  const [outcome] = useState(readOutcome)

  useEffect(() => {
    if (outcome.status !== 'ok') return
    setAccessToken(outcome.access)
    storeRefreshToken(outcome.refresh)
    // clear the fragment from history so tokens don't linger in the URL bar
    const t = window.setTimeout(() => window.location.replace('/home'), 600)
    return () => window.clearTimeout(t)
  }, [outcome])

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--bg)] p-4">
      <div className="animate-fade-up w-full max-w-sm rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-8 text-center">
        {outcome.status === 'ok' ? (
          <>
            <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-[var(--border)] border-t-indigo-400" />
            <h1 className="mt-4 text-lg font-semibold">Signed in with Google</h1>
            <p className="mt-2 text-sm text-[var(--muted)]">Taking you to Connectly…</p>
          </>
        ) : (
          <>
            <p className="text-4xl">⚠️</p>
            <h1 className="mt-4 text-lg font-semibold">Sign-in failed</h1>
            <p className="mt-2 text-sm text-[var(--muted)]">{outcome.message}</p>
            <Link to="/login" className="mt-6 inline-block text-sm font-medium text-indigo-400">← Back to login</Link>
          </>
        )}
      </div>
    </div>
  )
}
