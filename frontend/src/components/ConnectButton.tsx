import { useAppStore } from '../store/appStore'

export function ConnectButton({ userId, size = 'md' }: { userId: string; size?: 'sm' | 'md' }) {
  const status = useAppStore((s) => s.connectionStatuses[userId] ?? 'none')
  const send = useAppStore((s) => s.sendConnectionRequest)
  const accept = useAppStore((s) => s.acceptConnection)

  const base =
    size === 'sm'
      ? 'rounded-lg px-3 py-1.5 text-xs font-medium transition-all active:scale-95'
      : 'rounded-xl px-4 py-2 text-sm font-medium transition-all active:scale-95'

  if (status === 'connected') {
    return (
      <button disabled className={`${base} bg-emerald-500/15 text-emerald-500`} aria-label="Connected">
        ✓ Connected
      </button>
    )
  }
  if (status === 'request_sent') {
    return (
      <button disabled className={`${base} bg-[var(--surface-2)] text-[var(--muted)]`} aria-label="Request sent">
        ⏳ Pending
      </button>
    )
  }
  if (status === 'request_received') {
    return (
      <button onClick={() => accept(userId)} className={`${base} bg-emerald-600 text-white hover:bg-emerald-500`}>
        Accept
      </button>
    )
  }
  return (
    <button onClick={() => send(userId)} className={`${base} bg-[var(--accent)] text-white hover:bg-[var(--accent-hover)]`}>
      Connect
    </button>
  )
}
