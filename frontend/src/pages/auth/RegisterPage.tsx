import { useMemo, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiErrorMessage } from '../../lib/api'
import { useAuthStore } from '../../store/authStore'
import { useAppStore } from '../../store/appStore'

interface FieldErrors {
  firstName?: string
  username?: string
  email?: string
  password?: string
  confirm?: string
  code?: string
  server?: string
}

function passwordStrength(pw: string): { score: number; label: string; color: string } {
  let score = 0
  if (pw.length >= 10) score++
  if (/[A-Z]/.test(pw)) score++
  if (/[0-9]/.test(pw)) score++
  if (/[^A-Za-z0-9]/.test(pw)) score++
  const labels = ['too short', 'weak', 'okay', 'good', 'strong']
  const colors = ['bg-rose-500', 'bg-rose-500', 'bg-amber-500', 'bg-emerald-500', 'bg-emerald-500']
  return { score, label: labels[score], color: colors[score] }
}

export function RegisterPage() {
  const register = useAuthStore((s) => s.register)
  const verifyRegistrationOtp = useAuthStore((s) => s.verifyRegistrationOtp)
  const resendRegistrationOtp = useAuthStore((s) => s.resendRegistrationOtp)
  const pushToast = useAppStore((s) => s.pushToast)
  const navigate = useNavigate()

  const [form, setForm] = useState({
    firstName: '', lastName: '', username: '', email: '', password: '', confirm: '',
  })
  const [errors, setErrors] = useState<FieldErrors>({})
  const [busy, setBusy] = useState(false)

  // OTP step state — the account exists but stays dormant until the code is confirmed.
  const [step, setStep] = useState<'form' | 'otp'>('form')
  const [pendingEmail, setPendingEmail] = useState('')
  const [maskedEmail, setMaskedEmail] = useState('')
  const [code, setCode] = useState('')
  const [resending, setResending] = useState(false)
  const otpInput = useRef<HTMLInputElement>(null)

  const strength = useMemo(() => passwordStrength(form.password), [form.password])

  const set = (k: keyof typeof form) => (e: ChangeEvent<HTMLInputElement>) => {
    setForm({ ...form, [k]: e.target.value })
    setErrors((prev) => ({ ...prev, [k]: undefined, server: undefined }))
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const errs: FieldErrors = {}
    if (!form.firstName.trim()) errs.firstName = 'First name is required'
    if (!/^[a-z0-9_.]{3,20}$/.test(form.username)) errs.username = '3–20 chars: lowercase letters, numbers, _ or .'
    if (!/^\S+@\S+\.\S+$/.test(form.email)) errs.email = 'Enter a valid email'
    if (form.password.length < 10) errs.password = 'At least 10 characters'
    if (form.confirm !== form.password) errs.confirm = 'Passwords do not match'
    setErrors(errs)
    if (Object.keys(errs).length > 0) return

    setBusy(true)
    try {
      const { maskedEmail: masked } = await register({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        username: form.username.toLowerCase(),
        email: form.email.toLowerCase(),
        password: form.password,
      })
      setPendingEmail(form.email.toLowerCase())
      setMaskedEmail(masked || form.email)
      setStep('otp')
      setTimeout(() => otpInput.current?.focus(), 50)
    } catch (err) {
      setErrors({ server: apiErrorMessage(err) })
    } finally {
      setBusy(false)
    }
  }

  const submitOtp = async (e: FormEvent) => {
    e.preventDefault()
    if (!/^\d{6}$/.test(code)) {
      setErrors({ code: 'Enter the 6-digit code from your email' })
      return
    }
    setBusy(true)
    setErrors({})
    try {
      await verifyRegistrationOtp(pendingEmail, code)
      pushToast('Account activated — welcome to Connectly! 🎉', '✅')
      navigate('/home')
    } catch (err) {
      setErrors({ code: apiErrorMessage(err) })
    } finally {
      setBusy(false)
    }
  }

  const resend = async () => {
    setResending(true)
    try {
      await resendRegistrationOtp(pendingEmail)
      pushToast('A new activation code is on its way', '📬')
    } catch (err) {
      setErrors({ code: apiErrorMessage(err) })
    } finally {
      setResending(false)
    }
  }

  const inputCls = (bad?: string) =>
    `w-full rounded-xl border bg-[var(--surface-2)] px-3 py-2.5 text-sm outline-none transition-colors ${
      bad ? 'border-rose-500' : 'border-[var(--border)] focus:border-[var(--accent)]'
    }`

  if (step === 'otp') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[var(--bg)] p-4">
        <div className="animate-fade-up w-full max-w-md rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-8">
          <div className="mb-6 text-center">
            <p className="text-3xl font-bold text-indigo-400">Connectly</p>
            <p className="mt-1 text-sm text-[var(--muted)]">Check your email</p>
          </div>

          <p className="mb-6 text-center text-sm text-[var(--muted)]">
            We sent a 6-digit activation code to <strong className="text-[var(--text)]">{maskedEmail}</strong>.
            Enter it below to activate your account — the code expires in 15 minutes.
          </p>

          <form className="space-y-4" onSubmit={submitOtp} noValidate>
            <div>
              <label htmlFor="reg-otp" className="mb-1 block text-sm font-medium">Activation code</label>
              <input
                id="reg-otp"
                ref={otpInput}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                value={code}
                onChange={(e) => {
                  setCode(e.target.value.replace(/\D/g, '').slice(0, 6))
                  setErrors((prev) => ({ ...prev, code: undefined, server: undefined }))
                }}
                className={`${inputCls(errors.code)} text-center text-2xl tracking-[0.5em]`}
                placeholder="••••••"
              />
              {errors.code && <p className="mt-1 text-xs text-rose-500">{errors.code}</p>}
            </div>

            <button
              type="submit"
              disabled={busy || code.length !== 6}
              className="w-full rounded-xl bg-[var(--accent)] py-2.5 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] active:scale-[0.98] disabled:opacity-50"
            >
              {busy ? 'Activating…' : 'Activate account'}
            </button>

            <button
              type="button"
              onClick={resend}
              disabled={resending}
              className="w-full rounded-xl border border-[var(--border)] py-2.5 text-sm font-medium text-[var(--muted)] transition-colors hover:bg-[var(--surface-2)] disabled:opacity-50"
            >
              {resending ? 'Sending…' : 'Resend code'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-[var(--muted)]">
            Wrong address?{' '}
            <button
              type="button"
              className="font-medium text-indigo-400 hover:text-indigo-300"
              onClick={() => {
                setStep('form')
                setCode('')
                setErrors({})
              }}
            >
              Go back
            </button>
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--bg)] p-4">
      <div className="animate-fade-up w-full max-w-md rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-8">
        <div className="mb-6 text-center">
          <p className="text-3xl font-bold text-indigo-400">Connectly</p>
          <p className="mt-1 text-sm text-[var(--muted)]">Create your account</p>
        </div>

        <form className="space-y-4" onSubmit={submit} noValidate>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="reg-first" className="mb-1 block text-sm font-medium">First name</label>
              <input id="reg-first" value={form.firstName} onChange={set('firstName')} className={inputCls(errors.firstName)} placeholder="Asha" />
              {errors.firstName && <p className="mt-1 text-xs text-rose-500">{errors.firstName}</p>}
            </div>
            <div>
              <label htmlFor="reg-last" className="mb-1 block text-sm font-medium">Last name</label>
              <input id="reg-last" value={form.lastName} onChange={set('lastName')} className={inputCls()} placeholder="K" />
            </div>
          </div>

          <div>
            <label htmlFor="reg-username" className="mb-1 block text-sm font-medium">Username</label>
            <input id="reg-username" value={form.username} onChange={set('username')} className={inputCls(errors.username)} placeholder="asha_k" />
            {errors.username && <p className="mt-1 text-xs text-rose-500">{errors.username}</p>}
          </div>

          <div>
            <label htmlFor="reg-email" className="mb-1 block text-sm font-medium">Email</label>
            <input id="reg-email" type="email" value={form.email} onChange={set('email')} className={inputCls(errors.email)} placeholder="asha@example.com" />
            {errors.email && <p className="mt-1 text-xs text-rose-500">{errors.email}</p>}
          </div>

          <div>
            <label htmlFor="reg-password" className="mb-1 block text-sm font-medium">Password</label>
            <input id="reg-password" type="password" value={form.password} onChange={set('password')} className={inputCls(errors.password)} placeholder="At least 10 characters" />
            {form.password && (
              <div className="mt-1.5 flex items-center gap-2">
                <div className="flex flex-1 gap-1">
                  {[0, 1, 2, 3].map((i) => (
                    <span key={i} className={`h-1 flex-1 rounded-full ${i < strength.score ? strength.color : 'bg-[var(--border)]'}`} />
                  ))}
                </div>
                <span className="text-[11px] text-[var(--muted)]">{strength.label}</span>
              </div>
            )}
            {errors.password && <p className="mt-1 text-xs text-rose-500">{errors.password}</p>}
          </div>

          <div>
            <label htmlFor="reg-confirm" className="mb-1 block text-sm font-medium">Confirm password</label>
            <input id="reg-confirm" type="password" value={form.confirm} onChange={set('confirm')} className={inputCls(errors.confirm)} placeholder="••••••••" />
            {errors.confirm && <p className="mt-1 text-xs text-rose-500">{errors.confirm}</p>}
          </div>

          {errors.server && <p className="rounded-lg bg-rose-500/10 px-3 py-2 text-xs text-rose-500">{errors.server}</p>}

          <button
            type="submit"
            disabled={busy}
            className="w-full rounded-xl bg-[var(--accent)] py-2.5 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] active:scale-[0.98] disabled:opacity-50"
          >
            {busy ? 'Creating…' : 'Create account'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-[var(--muted)]">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-indigo-400 hover:text-indigo-300">Log in</Link>
        </p>
      </div>
    </div>
  )
}
